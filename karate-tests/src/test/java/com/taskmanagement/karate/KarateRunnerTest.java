package com.taskmanagement.karate;

import com.intuit.karate.junit5.Karate;

class KarateRunnerTest {

    @Karate.Test
    Karate auth() {
        return Karate.run("classpath:features/auth.feature");
    }

    @Karate.Test
    Karate tasks() {
        return Karate.run("classpath:features/tasks.feature");
    }

    @Karate.Test
    Karate isolation() {
        return Karate.run("classpath:features/isolation.feature");
    }
}
