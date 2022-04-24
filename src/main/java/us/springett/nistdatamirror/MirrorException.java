/*
 * This file is part of nist-data-mirror.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package us.springett.nistdatamirror;

import java.io.IOException;

/**
 * An exception used when the there is an error mirroring the NVD CVE.
 *
 * @author Jeremy Long
 */
public class MirrorException extends IOException {

    /**
     * The serial version UID for serialization.
     */
    private static final long serialVersionUID = 2048042874653986535L;

    /**
     * Creates a new MirrorException.
     *
     * @param msg a message for the exception.
     * @param ex the cause of the exception.
     */
    public MirrorException(String msg, Throwable ex) {
        super(msg, ex);
    }
}
