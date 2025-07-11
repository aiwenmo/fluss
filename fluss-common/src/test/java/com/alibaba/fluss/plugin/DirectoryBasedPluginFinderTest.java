/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fluss.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.fluss.utils.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** Test for {@link com.alibaba.fluss.plugin.DirectoryBasedPluginFinder}. */
public class DirectoryBasedPluginFinderTest {

    @Test
    void createPluginDescriptorsForDirectory(@TempDir Path temporaryFolder) throws Exception {
        File rootFolder = Files.createTempDirectory(temporaryFolder, "plugin").toFile();
        PluginFinder descriptorsFactory = new DirectoryBasedPluginFinder(rootFolder.toPath());
        Collection<PluginDescriptor> actual = descriptorsFactory.findPlugins();

        assertThat(actual.isEmpty()).as("empty root dir -> expected no actual").isTrue();

        List<File> subDirs =
                Stream.of("A", "B", "C")
                        .map(s -> new File(rootFolder, s))
                        .collect(Collectors.toList());

        for (File subDir : subDirs) {
            checkState(subDir.mkdirs());
        }

        try {
            descriptorsFactory.findPlugins();
            fail("all empty plugin sub-dirs");
        } catch (RuntimeException expected) {
            assertThat(expected.getCause() instanceof IOException).isTrue();
        }

        for (File subDir : subDirs) {
            // we create a file and another subfolder to check that they are ignored
            checkState(new File(subDir, "ignore-test.zip").createNewFile());
            checkState(new File(subDir, "ignore-dir").mkdirs());
        }

        try {
            descriptorsFactory.findPlugins();
            fail("still no jars in plugin sub-dirs");
        } catch (RuntimeException expected) {
            assertThat(expected.getCause() instanceof IOException).isTrue();
        }

        List<PluginDescriptor> expected = new ArrayList<>(3);

        for (int i = 0; i < subDirs.size(); ++i) {
            File subDir = subDirs.get(i);
            URL[] jarURLs = new URL[i + 1];

            for (int j = 0; j <= i; ++j) {
                File file = new File(subDir, "jar-file-" + j + ".jar");
                checkState(file.createNewFile());
                jarURLs[j] = file.toURI().toURL();
            }

            Arrays.sort(jarURLs, Comparator.comparing(URL::toString));
            expected.add(new PluginDescriptor(subDir.getName(), jarURLs, new String[0]));
        }

        actual = descriptorsFactory.findPlugins();

        assertThat(equalsIgnoreOrder(expected, new ArrayList<>(actual))).isTrue();
    }

    private boolean equalsIgnoreOrder(List<PluginDescriptor> a, List<PluginDescriptor> b) {

        if (a.size() != b.size()) {
            return false;
        }

        final Comparator<PluginDescriptor> comparator =
                Comparator.comparing(PluginDescriptor::getPluginId);

        a.sort(comparator);
        b.sort(comparator);

        final Iterator<PluginDescriptor> iterA = a.iterator();
        final Iterator<PluginDescriptor> iterB = b.iterator();

        while (iterA.hasNext()) {
            if (!equals(iterA.next(), iterB.next())) {
                return false;
            }
        }
        return true;
    }

    private static boolean equals(@Nonnull PluginDescriptor a, @Nonnull PluginDescriptor b) {
        return a.getPluginId().equals(b.getPluginId())
                && Arrays.deepEquals(a.getPluginResourceURLs(), b.getPluginResourceURLs())
                && Arrays.deepEquals(a.getLoaderExcludePatterns(), b.getLoaderExcludePatterns());
    }
}
