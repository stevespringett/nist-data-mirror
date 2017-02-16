nist-data-mirror
================

A simple Java command-line utility to mirror the CPE/CVE XML data from NIST.

The intended purpose of nist-data-mirror is to be able to replicate the NIST vulnerabiity data 
inside a company firewall so that local (faster) access to NIST data can be achieved.

nist-data-mirror does not rely on any third-party dependencies, only the Java SE core libraries. 
It can be used in combination with [OWASP Dependency-Check] in order to provide Dependency-Check 
a mirrored copy of NIST data.

For best results, use nist-data-mirror with cron or another scheduler to keep the mirrored data fresh.

build and deploy
----------------

### Build

```sh
gradlew clean build
```

### Deploy (to your nexus instance)

Note that for this to work you must update the `<>` values in `init.gradle`.

```sh
gradle -I init.gradle clean uploadArchives
```

### Run it

```sh
java -jar build\libs\nist-data-mirror-1.0.0.jar <output directory>
```

### Next steps

See https://jeremylong.github.io/DependencyCheck/data/mirrornvd.html

Copyright & License
-------------------

nist-data-mirror is Copyright (c) 2013 Steve Springett. All Rights Reserved.

Dependency-Check is Copyright (c) 2012-2013 Jeremy Long. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the GPLv3 license. See the [LICENSE] [GPLv3] file for the full license.

  [OWASP Dependency-Check]: https://www.owasp.org/index.php/OWASP_Dependency_Check
  [GPLv3]: https://github.com/stevespringett/nist-data-mirror/blob/master/LICENSE
