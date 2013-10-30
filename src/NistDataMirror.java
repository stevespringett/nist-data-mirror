/*
 * This file is part of nist-data-mirror.
 *
 * nist-data-mirror is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * nist-data-mirror is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * nist-data-mirror. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2013 Steve Springett. All Rights Reserved.
 */
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;

/**
 * This self-contained class can be called from the command-line. It downloads the
 * contents of NIST CPE/CVE XML data to the specified output path.
 *
 * @author Steve Springett (steve.springett@owasp.org)
 */
public class NistDataMirror {

    private static final String CVE_12_MODIFIED_URL = "http://nvd.nist.gov/download/nvdcve-modified.xml";
    private static final String CVE_20_MODIFIED_URL = "http://static.nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-modified.xml";
    private static final String CVE_12_BASE_URL = "http://nvd.nist.gov/download/nvdcve-%d.xml";
    private static final String CVE_20_BASE_URL = "http://static.nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-%d.xml";
    private static final int START_YEAR = 2002;
    private static final int END_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private String outputPath;

    public static void main (String[] args) throws Exception {
        // Ensure at least one argument was specified
        if (args.length != 1) {
            System.out.println("Usage: java NistDataMirror outputpath");
            return;
        }
        // Check for a trailing slash and add one if it doesn't exist
        String outputPath = args[0];
        if(outputPath.charAt(outputPath.length()-1) != File.separatorChar) {
            outputPath += File.separator;
        }

        NistDataMirror mirror = new NistDataMirror();
        mirror.setOutputPath(outputPath);
        mirror.doDownload(CVE_12_MODIFIED_URL);
        mirror.doDownload(CVE_20_MODIFIED_URL);
        for (int i=START_YEAR; i<=END_YEAR; i++) {
            String cve12BaseUrl = CVE_12_BASE_URL.replace("%d", String.valueOf(i));
            String cve20BaseUrl = CVE_20_BASE_URL.replace("%d", String.valueOf(i));
            mirror.doDownload(cve12BaseUrl);
            mirror.doDownload(cve20BaseUrl);
        }
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    private void doDownload(String cveUrl) throws IOException {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            URL url = new URL(cveUrl);
            URLConnection urlConnection = url.openConnection();
            System.out.println("Downloading " + url.toExternalForm());

            String filename = url.getFile();
            filename = filename.substring(filename.lastIndexOf('/') + 1);

            bis = new BufferedInputStream(urlConnection.getInputStream());
            File file = new File(outputPath + filename);
            bos = new BufferedOutputStream(new FileOutputStream(file));

            int i;
            while ((i = bis.read()) != -1) {
                bos.write( i );
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(bis);
            close(bos);
        }
    }

    private void close (Closeable object) {
        if (object != null) {
            try {
                object.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
