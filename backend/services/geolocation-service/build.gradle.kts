plugins {
    id("delivery.jpa-conventions")
    id("delivery.messaging-conventions")
    id("delivery.redis-conventions")
}

dependencies {
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation(libs.paho.mqtt.v5)
}
