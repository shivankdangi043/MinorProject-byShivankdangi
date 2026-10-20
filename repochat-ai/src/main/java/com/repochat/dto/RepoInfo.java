package com.repochat.dto;

/** A repository that has been cloned and indexed. */
public record RepoInfo(String id, String url, int files, int chunks) {}
