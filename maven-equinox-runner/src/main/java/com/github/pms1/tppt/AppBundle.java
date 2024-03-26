package com.github.pms1.tppt;

import java.io.Serializable;
import java.net.URL;

public record AppBundle(URL path, Integer startLevel, boolean autoStart) implements Serializable {
}
