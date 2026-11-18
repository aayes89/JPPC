/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pcp.xenon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XexImportResolver {

    public static final class ResolvedImport {

        public final String library;
        public final long thunkVA;
        public final XexImportThunk thunk;

        ResolvedImport(
                String library,
                long thunkVA,
                XexImportThunk thunk) {

            this.library = library;
            this.thunkVA = thunkVA;
            this.thunk = thunk;
        }
    }

    private XexImportResolver() {
    }

    public static List<ResolvedImport> inspect(
            byte[] peImage,
            long imageBase,
            List<XexImportLibraryParser.ImportLibrary> libraries) {

        if (peImage == null || libraries == null) {
            throw new IllegalArgumentException(
                    "Imagen o bibliotecas nulas");
        }

        List<ResolvedImport> result = new ArrayList<>();

        for (XexImportLibraryParser.ImportLibrary library
                : libraries) {

            for (XexImportLibraryParser.ImportDescriptor descriptor
                    : library.descriptors) {

                XexImportThunk thunk = XexImportThunk.read(
                        peImage,
                        imageBase,
                        descriptor.firstThunk);

                result.add(new ResolvedImport(
                        library.name,
                        descriptor.firstThunk,
                        thunk));
            }
        }

        return Collections.unmodifiableList(result);
    }
}
