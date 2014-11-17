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
 * Copyright (c) 2014 Steve Springett. All Rights Reserved.
 */
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.GZIPInputStream;

/**
 * This self-contained class can be called from the command-line. It downloads the
 * contents of NIST CPE/CVE XML data to the specified output path.
 *
 * @author Steve Springett (steve.springett@owasp.org)
 */
public class NistDataMirror {

    private static final String CVE_12_MODIFIED_URL = "https://nvd.nist.gov/download/nvdcve-Modified.xml.gz";
    private static final String CVE_20_MODIFIED_URL = "https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-Modified.xml.gz";
    private static final String CVE_12_BASE_URL = "https://nvd.nist.gov/download/nvdcve-%d.xml.gz";
    private static final String CVE_20_BASE_URL = "https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-%d.xml.gz";
    private static final int START_YEAR = 2002;
    private static final int END_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private File outputDir;
    private static boolean downloadFailed = false;

    public static void main (String[] args) {
        // Ensure at least one argument was specified
        if (args.length != 1) {
            System.out.println("Usage: java NistDataMirror outputDir");
            return;
        }
        NistDataMirror mirror = new NistDataMirror();
        mirror.setOutputDir(args[0]);
        mirror.getAllFiles();
        if (downloadFailed) {
          System.exit(1);
        }
    }

    private void getAllFiles() {
        Date currentDate = new Date();
        System.out.println("Downloading files at " + currentDate);

        doDownload(CVE_12_MODIFIED_URL);
        doDownload(CVE_20_MODIFIED_URL);
        for (int i=START_YEAR; i<=END_YEAR; i++) {
            String cve12BaseUrl = CVE_12_BASE_URL.replace("%d", String.valueOf(i));
            String cve20BaseUrl = CVE_20_BASE_URL.replace("%d", String.valueOf(i));
            doDownload(cve12BaseUrl);
            doDownload(cve20BaseUrl);
        }
    }

    public void setOutputDir(String outputDirPath) {
        outputDir = new File(outputDirPath);
        if ( ! outputDir.exists()) {
          outputDir.mkdirs();
        }
    }

    private void doDownload(String cveUrl) {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        File file = null;
        boolean success = false;
        try {
            URL url = new URL(cveUrl);
            URLConnection urlConnection = url.openConnection();
            System.out.println("Downloading " + url.toExternalForm());

            String filename = url.getFile();
            filename = filename.substring(filename.lastIndexOf('/') + 1);

            bis = new BufferedInputStream(urlConnection.getInputStream());
            file = new File(outputDir, filename);
            bos = new BufferedOutputStream(new FileOutputStream(file));

            int i;
            while ((i = bis.read()) != -1) {
                bos.write(i);
            }
            success = true;
        } catch (IOException e) {
            System.out.println("Download failed : " + e.getLocalizedMessage());
            downloadFailed = true;
        } finally {
            close(bis);
            close(bos);
        }
        if (file != null && success)
            uncompress(file);
    }

    public void uncompress(File file) {
        byte[] buffer = new byte[1024];
        GZIPInputStream gzis = null;
        FileOutputStream out = null;
        try{
            System.out.println("Uncompressing " + file.getName());
            gzis = new GZIPInputStream(new FileInputStream(file));
            out = new FileOutputStream(new File(file.getAbsolutePath().replaceAll(".gz", "")));
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }catch(IOException ex){
            ex.printStackTrace();
        } finally {
            close(gzis);
            close(out);
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
