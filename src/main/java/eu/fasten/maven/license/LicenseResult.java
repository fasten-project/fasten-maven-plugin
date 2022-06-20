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
package eu.fasten.maven.license;

import org.json.JSONObject;

public class LicenseResult
{
    private final String message;

    private final LicenseResultType status;

    private final String inbound;

    private final String spdxInbound;

    private final String outbound;

    private final String spdxOutbound;

    public enum LicenseResultType
    {
        COMPATIBLE,

        NOT_COMPATIBLE,

        UNKNOWN
    }

    public LicenseResult(JSONObject message)
    {
        this.message = message.getString("message");

        String statusString = message.getString("status");

        if (statusString.equals("compatible")) {
            this.status = LicenseResultType.COMPATIBLE;
        } else if (statusString.equals("not compatible")) {
            this.status = LicenseResultType.NOT_COMPATIBLE;
        } else {
            this.status = LicenseResultType.UNKNOWN;
        }

        this.inbound = message.optString("inbound", null);
        this.spdxInbound = message.optString("inbound_SPDX", null);
        this.outbound = message.optString("outbound", null);
        this.spdxOutbound = message.optString("outbound_SPDX", null);
    }

    /**
     * @return the message
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * @return the status
     */
    public LicenseResultType getStatus()
    {
        return this.status;
    }

    /**
     * @return the inbound license name
     */
    public String getInbound()
    {
        return this.inbound;
    }

    /**
     * @return the SPDX version of the inbound license name
     */
    public String getSpdxInbound()
    {
        return this.spdxInbound;
    }

    /**
     * @return the outbound license name
     */
    public String getOutbound()
    {
        return this.outbound;
    }

    /**
     * @return the SPDX version of the outbound license name
     */
    public String getSpdxOutbound()
    {
        return this.spdxOutbound;
    }
}
