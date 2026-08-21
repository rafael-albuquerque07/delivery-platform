import org.gradle.accessors.dm.LibrariesForLibs

val libs = the<LibrariesForLibs>()

plugins {
    id("delivery.jpa-conventions")
    id("delivery.messaging-conventions")
    id("delivery.redis-conventions")
}

dependencies {
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation(libs.paho.mqtt.v5)
}
