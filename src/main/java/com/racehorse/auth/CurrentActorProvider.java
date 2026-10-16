package com.racehorse.auth;

import java.util.Optional;

public interface CurrentActorProvider {

    Optional<ActorContext> currentActor();
}