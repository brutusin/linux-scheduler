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
package org.brutusin.wava.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.brutusin.commons.Bean;
import org.brutusin.commons.utils.Miscellaneous;
import org.brutusin.json.spi.JsonCodec;
import org.brutusin.json.spi.JsonNode;
import org.brutusin.wava.core.Environment;
import org.brutusin.wava.core.Event;
import org.brutusin.wava.core.PeerChannel;
import org.brutusin.wava.core.plug.LinuxCommands;
import org.brutusin.wava.data.OpName;

/**
 *
 * @author Ignacio del Valle Alles idelvall@brutusin.org
 */
public final class Utils {

    public final static int WAVA_ERROR_RETCODE = 2016;
    public final static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private Utils() {
    }

    public static List<String> parseEventLine(String line) {
        List<String> ret = new ArrayList<>();
        int start = 0;
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && line.charAt(i) == ':') {
                ret.add(line.substring(start, i));
                start = i + 1;
            }
        }
        if (start < line.length()) {
            ret.add(line.substring(start));
        }
        return ret;
    }

    public static void showHelp(Options options, String commandLine) {
        HelpFormatter formatter = new HelpFormatter();
        PrintWriter pw = new PrintWriter(System.err);
        formatter.printHelp(pw, Integer.MAX_VALUE, commandLine, null, options, 4, 4, null);
        pw.flush();
    }

    public static FileLock tryLock(File f) throws IOException {
        if (!f.exists()) {
            Miscellaneous.createFile(f.getAbsolutePath());
        }
        RandomAccessFile raf = new RandomAccessFile(f, "rws");
        return raf.getChannel().tryLock();
    }

    public static void validateCoreRunning() throws IOException {
        FileLock lock = Utils.tryLock(new File(Environment.ROOT, ".lock"));
        if (lock != null) {
            System.err.println(ANSIColor.RED.getCode() + "WAVA core process is not running!" + ANSIColor.RESET.getCode());
            System.exit(-2);
        }
    }

    public static Integer executeRequest(OpName opName, Object input, final OutputStream eventStream, boolean prettyEvents) throws IOException, InterruptedException {
        File counterFile = new File(Environment.ROOT, "state/.seq");
        long id = Miscellaneous.getGlobalAutoIncremental(counterFile);
        String json = JsonCodec.getInstance().transform(input);
        File requestFile = new File(Environment.ROOT, "request/" + id + "-" + opName);
        File streamRoot = new File(Environment.ROOT, "streams/" + id);
        Miscellaneous.createDirectory(streamRoot);
        File eventsNamedPipe = new File(streamRoot, PeerChannel.NamedPipe.events.name());
        File stdoutNamedPipe = new File(streamRoot, PeerChannel.NamedPipe.stdout.name());
        File stderrNamedPipe = new File(streamRoot, PeerChannel.NamedPipe.stderr.name());
        LinuxCommands.getInstance().createNamedPipes(eventsNamedPipe, stderrNamedPipe, stdoutNamedPipe);
        final Bean<Integer> retCode = new Bean<>();
        Thread eventsThread;
        eventsThread = new Thread() {
            @Override
            public void run() {
                try {
                    InputStream eventsIs = new FileInputStream(eventsNamedPipe);
                    BufferedReader br = new BufferedReader(new InputStreamReader(eventsIs));
                    String line;
                    while ((line = br.readLine()) != null) {
                        List<String> tokens = parseEventLine(line);
                        Event evt = Event.valueOf(tokens.get(0));
                        if (evt == Event.ping) {

                        } else if (eventStream != null) {
                            if (!prettyEvents) {
                                synchronized (eventStream) {
                                    eventStream.write((line + "\n").getBytes());
                                }
                            } else {
                                ANSIColor color = ANSIColor.GREEN;
                                String value = tokens.get(2);
                                if (evt == Event.id || evt == Event.running) {
                                    color = ANSIColor.CYAN;
                                } else if (evt == Event.queued) {
                                    color = ANSIColor.YELLOW;
                                } else if (evt == Event.retcode) {
                                    color = ANSIColor.RED;
                                } else if (evt == Event.error) {
                                    color = ANSIColor.RED;
                                    JsonNode node = JsonCodec.getInstance().parse(value);
                                    value = node.asString();
                                }
                                Date date = new Date(Long.valueOf(tokens.get(1)));
                                synchronized (eventStream) {
                                    eventStream.write((color.getCode() + "[wava] [" + Utils.DATE_FORMAT.format(date) + "] [" + evt + ":" + value + "]" + ANSIColor.RESET.getCode() + "\n").getBytes());
                                }
                            }
                        } else if (evt == Event.retcode) {
                            JsonNode node = JsonCodec.getInstance().parse(tokens.get(2));
                            retCode.setValue(node.asInteger());
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        };
        Thread outThread = new Thread() {
            @Override
            public void run() {
                try {
                    InputStream outIs = new FileInputStream(stdoutNamedPipe);
                    Miscellaneous.pipeAsynchronously(outIs, System.out);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        };
        Thread errThread = new Thread() {
            @Override
            public void run() {
                InputStream errIs = null;
                try {
                    errIs = new FileInputStream(stderrNamedPipe);
                    BufferedReader br = new BufferedReader(new InputStreamReader(errIs));
                    Miscellaneous.pipeSynchronously(br, false, System.err);
                } catch (Throwable th) {
                    th.printStackTrace();
                } finally {
                    if (errIs != null) {
                        try {
                            errIs.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        };
        eventsThread.start();
        outThread.start();
        errThread.start();
        Miscellaneous.writeStringToFile(requestFile, json, "UTF-8");
        eventsThread.join();
        outThread.join();
        errThread.join();
        return retCode.getValue();
    }
}