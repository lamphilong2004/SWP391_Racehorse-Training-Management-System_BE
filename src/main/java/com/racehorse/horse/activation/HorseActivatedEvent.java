package com.racehorse.horse.activation;

/** Published inside the activation transaction when a DRAFT horse becomes ACTIVE (it has an owner and a box). */
public record HorseActivatedEvent(long horseId) {
}
