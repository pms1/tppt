package com.github.pms1.tppt.p2;

import java.io.IOException;

public interface CommonP2Repository {

	void setCompression(DataCompression... compressions) throws IOException;
}
