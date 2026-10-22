package com.racehorse.horse.photo;

import java.net.URI;
import java.time.Instant;

public record HorsePhoto(long id, URI url, Instant uploadedAt) {
}
