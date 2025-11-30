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
 * POJO representing the payload of a Polymarket comment event.
 */
public class CommentPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String body;
    private String createdAt;
    private String id;
    private String parentCommentID;
    private long parentEntityID;
    private String parentEntityType;
    private Profile profile;
    private int reactionCount;
    private String replyAddress;
    private int reportCount;
    private String userAddress;

    // Default constructor required for Jackson deserialization
    public CommentPayload() {
    }

    // Getters and Setters
    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentCommentID() {
        return parentCommentID;
    }

    public void setParentCommentID(String parentCommentID) {
        this.parentCommentID = parentCommentID;
    }

    public long getParentEntityID() {
        return parentEntityID;
    }

    public void setParentEntityID(long parentEntityID) {
        this.parentEntityID = parentEntityID;
    }

    public String getParentEntityType() {
        return parentEntityType;
    }

    public void setParentEntityType(String parentEntityType) {
        this.parentEntityType = parentEntityType;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public int getReactionCount() {
        return reactionCount;
    }

    public void setReactionCount(int reactionCount) {
        this.reactionCount = reactionCount;
    }

    public String getReplyAddress() {
        return replyAddress;
    }

    public void setReplyAddress(String replyAddress) {
        this.replyAddress = replyAddress;
    }

    public int getReportCount() {
        return reportCount;
    }

    public void setReportCount(int reportCount) {
        this.reportCount = reportCount;
    }

    public String getUserAddress() {
        return userAddress;
    }

    public void setUserAddress(String userAddress) {
        this.userAddress = userAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommentPayload that = (CommentPayload) o;
        return parentEntityID == that.parentEntityID &&
                reactionCount == that.reactionCount &&
                reportCount == that.reportCount &&
                Objects.equals(body, that.body) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(id, that.id) &&
                Objects.equals(parentCommentID, that.parentCommentID) &&
                Objects.equals(parentEntityType, that.parentEntityType) &&
                Objects.equals(profile, that.profile) &&
                Objects.equals(replyAddress, that.replyAddress) &&
                Objects.equals(userAddress, that.userAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, createdAt, id, parentCommentID, parentEntityID, 
                parentEntityType, profile, reactionCount, replyAddress, reportCount, userAddress);
    }

    @Override
    public String toString() {
        return "CommentPayload{" +
                "body='" + body + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", id='" + id + '\'' +
                ", parentCommentID='" + parentCommentID + '\'' +
                ", parentEntityID=" + parentEntityID +
                ", parentEntityType='" + parentEntityType + '\'' +
                ", profile=" + profile +
                ", reactionCount=" + reactionCount +
                ", replyAddress='" + replyAddress + '\'' +
                ", reportCount=" + reportCount +
                ", userAddress='" + userAddress + '\'' +
                '}';
    }
}
