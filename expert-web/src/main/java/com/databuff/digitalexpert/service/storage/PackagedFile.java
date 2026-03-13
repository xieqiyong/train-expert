package com.databuff.digitalexpert.service.storage;

import java.nio.file.Path;

public record PackagedFile(String entryPath, Path sourcePath) {
}

