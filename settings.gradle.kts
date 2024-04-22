/*
 * Copyright 2024 Mark Slater
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.8.0")
}

rootProject.name = "svg2ico"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            plugin("spotbugs", "com.github.spotbugs").version("6.0.9")
            plugin("shadow", "com.github.johnrengelman.shadow").version("8.1.1")
            plugin("nexusPublish", "io.github.gradle-nexus.publish-plugin").version("2.0.0")
            plugin("svg2ico", "com.gitlab.svg2ico").version("1.4")
            plugin("asciidoctorConvert", "org.asciidoctor.jvm.convert").version("4.0.2")

            library("ant", "org.apache.ant", "ant").version("1.10.14")
            library("batikRasterizer", "org.apache.xmlgraphics", "batik-rasterizer").version("1.17")
            library("commonsCli", "commons-cli", "commons-cli").version("1.6.0")
            library("image4j", "org.jclarion", "image4j").version("0.7")

            library("spotbugs", "com.github.spotbugs", "spotbugs").version("4.8.3")
        }
    }
}