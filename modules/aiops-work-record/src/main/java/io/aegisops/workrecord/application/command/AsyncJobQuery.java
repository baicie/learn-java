package io.aegisops.workrecord.application.command;

public record AsyncJobQuery(
    String userId, boolean readAll, String status, String jobType, Integer page, Integer size) {}
