/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.examples.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * POJO representing the profile object in Polymarket comment events.
 */
public class Profile implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseAddress;
    private boolean displayUsernamePublic;
    private String name;
    private String proxyWallet;
    private String pseudonym;

    // Default constructor required for Jackson deserialization
    public Profile() {
    }

    public Profile(String baseAddress, boolean displayUsernamePublic, String name, 
                   String proxyWallet, String pseudonym) {
        this.baseAddress = baseAddress;
        this.displayUsernamePublic = displayUsernamePublic;
        this.name = name;
        this.proxyWallet = proxyWallet;
        this.pseudonym = pseudonym;
    }

    // Getters and Setters
    public String getBaseAddress() {
        return baseAddress;
    }

    public void setBaseAddress(String baseAddress) {
        this.baseAddress = baseAddress;
    }

    public boolean isDisplayUsernamePublic() {
        return displayUsernamePublic;
    }

    public void setDisplayUsernamePublic(boolean displayUsernamePublic) {
        this.displayUsernamePublic = displayUsernamePublic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProxyWallet() {
        return proxyWallet;
    }

    public void setProxyWallet(String proxyWallet) {
        this.proxyWallet = proxyWallet;
    }

    public String getPseudonym() {
        return pseudonym;
    }

    public void setPseudonym(String pseudonym) {
        this.pseudonym = pseudonym;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return displayUsernamePublic == profile.displayUsernamePublic &&
                Objects.equals(baseAddress, profile.baseAddress) &&
                Objects.equals(name, profile.name) &&
                Objects.equals(proxyWallet, profile.proxyWallet) &&
                Objects.equals(pseudonym, profile.pseudonym);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseAddress, displayUsernamePublic, name, proxyWallet, pseudonym);
    }

    @Override
    public String toString() {
        return "Profile{" +
                "baseAddress='" + baseAddress + '\'' +
                ", displayUsernamePublic=" + displayUsernamePublic +
                ", name='" + name + '\'' +
                ", proxyWallet='" + proxyWallet + '\'' +
                ", pseudonym='" + pseudonym + '\'' +
                '}';
    }
}
