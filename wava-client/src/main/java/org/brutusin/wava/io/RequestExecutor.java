/*
 * Copyright 2016 Ignacio del Valle Alles idelvall@brutusin.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.brutusin.wava.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.brutusin.commons.Bean;
import org.brutusin.commons.utils.Miscellaneous;
import org.brutusin.commons.utils.ProcessUtils;
import org.brutusin.json.spi.JsonCodec;
import org.brutusin.wava.env.WavaTemp;
import org.brutusin.wava.Utils;
import org.brutusin.wava.input.Input;

/**
 *
 * @author Ignacio del Valle Alles idelvall@brutusin.org
 */
public class RequestExecutor {

    private static final Logger LOGGER = Logger.getLogger(RequestExecutor.class.getName());
    private static final File COUNTER_FILE = new File(WavaTemp.getInstance().getTemp(), "state/.seq");


    public Integer executeRequest(OpName opName, Input input, final InputStream stdinStream, final OutputStream stdoutStream, final LineListener stderrListener, final EventListener eventListener) throws IOException {
        long id = Miscellaneous.getGlobalAutoIncremental(COUNTER_FILE);
        String json = JsonCodec.getInstance().transform(input);
        File streamRoot = new File(WavaTemp.getInstance().getTemp(), "streams/" + id);
        Miscellaneous.createDirectory(streamRoot);
        final File stdinNamedPipe = new File(streamRoot, NamedPipe.stdin.name());
        final File eventsNamedPipe = new File(streamRoot, NamedPipe.events.name());
        final File stdoutNamedPipe = new File(streamRoot, NamedPipe.stdout.name());
        final File stderrNamedPipe = new File(streamRoot, NamedPipe.stderr.name());
        try {
            ProcessUtils.createPOSIXNamedPipes(stdinNamedPipe, eventsNamedPipe, stderrNamedPipe, stdoutNamedPipe);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        final Bean<Integer> retCode = new Bean<>();
        final Bean<FileOutputStream> stdinStreamBean = new Bean<>();
        final Bean<FileInputStream> eventsStreamBean = new Bean<>();
        final Bean<FileInputStream> stdoutStreamBean = new Bean<>();
        final Bean<FileInputStream> stderrStreamBean = new Bean<>();

        final Bean<Boolean> timeoutBean = new Bean<>();

        Thread stdinThread = new Thread() {
            @Override
            public void run() {
                FileOutputStream os = null;
                try {
                    os = new FileOutputStream(stdinNamedPipe);
                    synchronized (timeoutBean) {
                        if (timeoutBean.getValue() == null) {
                            timeoutBean.setValue(false);
                        } else if (timeoutBean.getValue() == true) {
                            return;
                        }
                    }
                    stdinStreamBean.setValue(os);
                    if (stdinStream != null) {
                        Miscellaneous.pipeSynchronously(stdinStream, true, os);
                    }
                } catch (Throwable th) {
                    LOGGER.log(Level.SEVERE, th.getMessage(), th);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        };
        stdinThread.setDaemon(true);
        stdinThread.start();

        Thread eventsThread = new Thread() {
            @Override
            public void run() {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(eventsNamedPipe);
                    synchronized (timeoutBean) {
                        if (timeoutBean.getValue() == null) {
                            timeoutBean.setValue(false);
                        } else if (timeoutBean.getValue() == true) {
                            return;
                        }
                    }
                    eventsStreamBean.setValue(is);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = br.readLine()) != null) {
                        List<String> tokens = Utils.parseEventLine(line);
                        Event evt = Event.valueOf(tokens.get(1));
                        String value;
                        if (tokens.size() > 2) {
                            value = tokens.get(2);
                            if (evt == Event.retcode) {
                                retCode.setValue(Integer.valueOf(value));
                            }
                        } else {
                            value = null;
                        }
                        if (eventListener != null) {
                            eventListener.onEvent(evt, value, Long.valueOf(tokens.get(0)));
                        }
                    }
                } catch (Throwable th) {
                    LOGGER.log(Level.SEVERE, th.getMessage(), th);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        };
        eventsThread.start();
        Thread outThread = new Thread() {
            @Override
            public void run() {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(stdoutNamedPipe);
                    synchronized (timeoutBean) {
                        if (timeoutBean.getValue() == null) {
                            timeoutBean.setValue(false);
                        } else if (timeoutBean.getValue() == true) {
                            return;
                        }
                    }
                    stdoutStreamBean.setValue(is);
                    Miscellaneous.pipeSynchronously(is, true, stdoutStream);
                } catch (Throwable th) {
                    LOGGER.log(Level.SEVERE, th.getMessage(), th);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        };
        outThread.start();
        Thread errThread = new Thread() {
            @Override
            public void run() {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(stderrNamedPipe);
                    synchronized (timeoutBean) {
                        if (timeoutBean.getValue() == null) {
                            timeoutBean.setValue(false);
                        } else if (timeoutBean.getValue() == true) {
                            return;
                        }
                    }
                    stderrStreamBean.setValue(is);
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (stderrListener != null) {
                            stderrListener.onNewLine(line);
                        }
                    }
                } catch (Throwable th) {
                    LOGGER.log(Level.SEVERE, th.getMessage(), th);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
        };
        errThread.start();

        Thread timeoutThread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10 * 1000); // If after this time the pipes have not been open for reading (nobody is using the other side) discard all
                    synchronized (timeoutBean) {
                        if (timeoutBean.getValue() == null) {
                            timeoutBean.setValue(true);
                            try {
                                new FileOutputStream(eventsNamedPipe).close();
                            } catch (Exception ex) {
                            }
                            try {
                                new FileOutputStream(stdoutNamedPipe).close();
                            } catch (Exception ex) {
                            }
                            try {
                                new FileOutputStream(stderrNamedPipe).close();
                            } catch (Exception ex) {
                            }
                            try {
                                new FileInputStream(stdinNamedPipe).close();
                            } catch (Exception ex) {
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        };
        timeoutThread.setDaemon(true);
        timeoutThread.start();

        File tempFile = new File(WavaTemp.getInstance().getTemp(), "temp/" + id + "-" + opName);
        File requestFile = new File(WavaTemp.getInstance().getTemp(), "request/" + id + "-" + opName);

        Files.write(tempFile.toPath(), json.getBytes());
        Files.move(tempFile.toPath(), requestFile.toPath());

        try {
            errThread.join();
            eventsThread.join();
            outThread.join();
        } catch (InterruptedException i) {
        } finally {
            if (stdinStreamBean.getValue() != null) {
                try {
                    stdinStreamBean.getValue().close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            if (eventsStreamBean.getValue() != null) {
                try {
                    eventsStreamBean.getValue().close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            if (stdoutStreamBean.getValue() != null) {
                try {
                    stdoutStreamBean.getValue().close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            if (stderrStreamBean.getValue() != null) {
                try {
                    stderrStreamBean.getValue().close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
        }
        return retCode.getValue();
    }

}
