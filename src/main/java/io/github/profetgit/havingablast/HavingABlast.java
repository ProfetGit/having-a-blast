package io.github.profetgit.havingablast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry: runs on both sides. Client-only setup lives in client/BlastClient, started by a client mixin. */
public final class HavingABlast {
    public static final String MOD_ID = "havingablast";
    public static final Logger LOG = LoggerFactory.getLogger("Having a Blast");
    private static String loader = "?";

    private HavingABlast() {
    }

    public static void init(String loaderName) {
        loader = loaderName;
        LOG.info("Having a Blast loaded on {}", loader);
    }

    public static String loader() {
        return loader;
    }
}
