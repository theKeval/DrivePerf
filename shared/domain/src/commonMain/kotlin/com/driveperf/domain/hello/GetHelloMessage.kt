package com.driveperf.domain.hello

/** Use case behind the scaffold screen: proves an app can call shared domain code on every platform. */
public class GetHelloMessage {
    public operator fun invoke(platformDescription: String): HelloMessage =
        HelloMessage(title = "Hello DrivePerf", subtitle = "Shared core running on $platformDescription")
}
