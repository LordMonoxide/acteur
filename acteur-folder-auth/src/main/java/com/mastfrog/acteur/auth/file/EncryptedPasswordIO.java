/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur.auth.file;

import com.google.inject.Inject;
import com.mastfrog.acteur.util.PasswordHasher;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public class EncryptedPasswordIO extends PlaintextPasswordIO {
    private final PasswordHasher enc;

    @Inject
    public EncryptedPasswordIO(PasswordHasher enc) {
        this.enc = enc;
    }

    @Override
    public void writePassword(String password, File file) throws IOException {
        super.writePassword(enc.encryptPassword(password), file);
    }

    @Override
    public boolean checkPassword(String password, File file) throws IOException {
        if (file == null || password == null || password.length() == 0) {
            return false;
        }
        String encrypted = super.readPassword(file);
        return enc.checkPassword(password, encrypted);
    }
}
