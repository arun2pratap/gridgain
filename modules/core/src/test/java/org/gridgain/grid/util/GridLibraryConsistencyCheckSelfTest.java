/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
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

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import junit.framework.*;
import org.gridgain.testframework.junits.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;

import static org.gridgain.grid.util.GridLibraryConsistencyCheck.*;

/**
 * Library consistency check tests.
 */
public class GridLibraryConsistencyCheckSelfTest extends GridCommonAbstractTest {
    /**
     *
     */
    public void testAllLibrariesLoaded() {
        ArrayList<String> libs = libraries();

        boolean testPassed = !libs.contains(NOT_FOUND_MESSAGE);

        String missedLibs = "";

        for (int i = 0; i < libs.size(); i++)
            if (NOT_FOUND_MESSAGE.equals(libs.get(i)))
                missedLibs += '\t' + CLASS_LIST[i] + '\n';

        String msg = testPassed ? "" : "Libraries with following classes is removed (if it is made on purpose " +
            "then remove this lib from " + GridLibraryConsistencyCheck.class.getSimpleName() + " class):\n" +
            missedLibs;

        assertTrue(msg, testPassed);
    }

    /**
     * @throws Exception If failed.
     */
    public void testCheckLibraryHasDifferentVersions() throws Exception {
        int libCnt = CLASS_LIST.length;

        List<String> libs1 = initLibs(libCnt);
        List<String> libs2 = initLibs(libCnt);

        libs1.add("lib-1.0.jar");
        libs2.add("lib-1.1.jar");

        assertEquals(1, check(log, libs1, libs2).size());
    }

    /**
     * @throws Exception If failed.
     */
    public void testCheckLibraryNotFound() throws Exception {
        int libCnt = CLASS_LIST.length;

        List<String> libs1 = initLibs(libCnt);
        List<String> libs2 = initLibs(libCnt);

        libs1.add("lib-1.0.jar");
        libs2.add(NOT_FOUND_MESSAGE);

        assertEquals(0, check(log, libs1, libs2).size());
    }

    /** Initializes libraries list with not_found messages. */
    private static List<String> initLibs(int libCnt) {
        List<String> libs = new ArrayList<>(libCnt);

        for (int i = 0; i < libCnt - 1; i++)
            libs.add(NOT_FOUND_MESSAGE);

        return libs;
    }
}
