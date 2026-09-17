plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
tasks.test { useJUnit() }
dependencies { testImplementation("junit:junit:4.13.2") }
