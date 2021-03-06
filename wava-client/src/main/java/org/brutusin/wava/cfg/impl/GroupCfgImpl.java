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
package org.brutusin.wava.cfg.impl;

import java.io.File;
import org.brutusin.wava.cfg.GroupCfg;

/**
 *
 * @author Ignacio del Valle Alles idelvall@brutusin.org
 */
public class GroupCfgImpl implements GroupCfg {

    private int dynamicGroupIdleSeconds = 10;
    public GroupImpl[] predefinedGroups = {new GroupImpl("high", -10, -1, null), new GroupImpl("low", 10, -1, null)};

    @Override
    public int getDynamicGroupIdleSeconds() {
        return dynamicGroupIdleSeconds;
    }

    public void setDynamicGroupIdleSeconds(int dynamicGroupIdleSeconds) {
        this.dynamicGroupIdleSeconds = dynamicGroupIdleSeconds;
    }

    @Override
    public GroupImpl[] getPredefinedGroups() {
        return predefinedGroups;
    }

    public void setPredefinedGroups(GroupImpl[] predefinedGroups) {
        this.predefinedGroups = predefinedGroups;
    }

    public static class GroupImpl implements GroupCfg.Group {

        private String name;
        private int priority;
        private int timeToIdleSeconds;
        private File statsDirectory;

        public GroupImpl() {
        }

        public GroupImpl(String name, int priority, int timeToIdleSeconds, File statsDirectory) {
            this.name = name;
            this.priority = priority;
            this.timeToIdleSeconds = timeToIdleSeconds;
            this.statsDirectory = statsDirectory;
        }

        @Override
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public int getTimeToIdleSeconds() {
            return timeToIdleSeconds;
        }

        public void setTimeToIdleSeconds(int timeToIdleSeconds) {
            this.timeToIdleSeconds = timeToIdleSeconds;
        }

        @Override
        public File getStatsDirectory() {
            return statsDirectory;
        }

        public void setStatsDirectory(File statsDirectory) {
            this.statsDirectory = statsDirectory;
        }
    }
}
