/*
  Copyright (C) 2013-2021 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.common.io;

import com.hotels.styx.api.Resource;

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hotels.styx.javaconvenience.UtilKt.uncheck;
import static java.nio.file.Files.walk;

/**
 * Iterates over resources in a file system location.
 */
public class FileResourceIterator implements Iterator<Resource> {
    private final Iterator<Resource> resourceIterator;

    public FileResourceIterator(File root, File file, String suffix) {
        Path absolutePath = root.toPath().resolve(file.toPath());
        this.resourceIterator = uncheck(() -> walk(absolutePath))
                .map(Path::toFile)
                .filter(hasSuffix(suffix).or(sameFile(file)))
                .map(fileToResource(file))
                .iterator();
    }

    private static Predicate<File> sameFile(File file) {
        return input -> input.isFile() && input.getAbsolutePath().equals(file.getAbsolutePath());
    }

    private static Function<File, Resource> fileToResource(File root) {
        return input -> new FileResource(root, input);
    }

    private static Predicate<File> hasSuffix(String suffix) {
        return file -> hasSuffix(suffix, file.getPath());
    }

    private static boolean hasSuffix(String suffix, String name) {
        return suffix == null || name.endsWith(suffix);
    }

    @Override
    public boolean hasNext() {
        return resourceIterator.hasNext();
    }

    @Override
    public Resource next() {
        return resourceIterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
