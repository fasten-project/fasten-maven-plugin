/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.maven.analyzer;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.helpers.MessageFormatter;

/**
 * @version $Id$
 */
public class RiskReport
{
    public class Message
    {
        final String messagePattern;

        final Object[] argArray;

        final Throwable throwable;

        public Message(String messagePattern, Object[] argArray, Throwable throwable)
        {
            this.messagePattern = messagePattern;
            this.argArray = argArray;
            this.throwable = throwable;
        }

        public Throwable getThrowable()
        {
            return this.throwable;
        }

        public String getFormattedMessage()
        {
            return MessageFormatter.arrayFormat(this.messagePattern, this.argArray, this.throwable).getMessage();
        }

        @Override
        public String toString()
        {
            return getFormattedMessage();
        }
    }

    private final RiskAnalyzer analyzer;

    private final List<Message> errors = new ArrayList<>();

    private final List<Message> warnings = new ArrayList<>();

    public RiskReport(RiskAnalyzer analizer)
    {
        this.analyzer = analizer;
    }

    /**
     * @return the analyzer
     */
    public RiskAnalyzer getAnalyzer()
    {
        return this.analyzer;
    }

    public void error(String messagePattern)
    {
        error(messagePattern, (Throwable) null);
    }

    public void error(String messagePattern, Throwable throwable)
    {
        error(messagePattern, ArrayUtils.EMPTY_OBJECT_ARRAY, throwable);
    }

    public void error(String messagePattern, Object... args)
    {
        message(true, messagePattern, args);
    }

    public void error(String messagePattern, Object[] argArray, Throwable throwable)
    {
        this.errors.add(new Message(messagePattern, argArray, throwable));
    }

    public void warning(String messagePattern)
    {
        warning(messagePattern, (Throwable) null);
    }

    public void warning(String messagePattern, Throwable throwable)
    {
        warning(messagePattern, ArrayUtils.EMPTY_OBJECT_ARRAY, throwable);
    }

    public void warning(String messagePattern, Object... args)
    {
        message(false, messagePattern, args);
    }

    public void warning(String messagePattern, Object[] argArray, Throwable throwable)
    {
        this.warnings.add(new Message(messagePattern, argArray, throwable));
    }

    private void message(boolean error, String messagePattern, Object... args)
    {
        Object[] argArray = args;
        Throwable throwable = null;

        if (argArray.length > 0) {
            Object lastArgument = args[args.length - 1];
            if (lastArgument instanceof Throwable) {
                argArray = ArrayUtils.subarray(args, 0, argArray.length - 1);
                throwable = (Throwable) lastArgument;
            }
        }

        if (error) {
            error(messagePattern, argArray, throwable);
        } else {
            warning(messagePattern, argArray, throwable);
        }
    }

    /**
     * @return the errors
     */
    public List<Message> getErrors()
    {
        return this.errors;
    }

    /**
     * @return the warnings
     */
    public List<Message> getWarnings()
    {
        return this.warnings;
    }
}
