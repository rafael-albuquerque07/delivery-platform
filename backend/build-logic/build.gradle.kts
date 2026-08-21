plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.findPlugin("spring-boot").get().map {
        "org.springframework.boot:spring-boot-gradle-plugin:${it.version}"
    })
    implementation(libs.findPlugin("spring-dependency-mgmt").get().map {
        "io.spring:dependency-management-plugin:${it.version}"
    })
}
