/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.gridgain.grid.*;
import org.gridgain.grid.product.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import static org.gridgain.grid.product.GridProductEdition.*;

/**
 * {@link GridProduct} implementation.
 */
public class GridProductImpl implements GridProduct {
    /** Copyright blurb. */
    public static final String COPYRIGHT = "2014 Copyright (C) GridGain Systems";

    /** Enterprise edition flag. */
    public static final boolean ENT;

    /** Edition name. */
    public static final String EDITION;

    /** GridGain version. */
    public static final String VER;

    /** Build number. */
    public static final long BUILD;

    /** Revision hash. */
    public static final String REV_HASH;

    /** Release date. */
    public static final String RELEASE_DATE;

    /** GridGain version as numeric array (generated from {@link #VER}). */
    public static final byte[] VER_BYTES;

    /** Compound version. */
    public static final String COMPOUND_VERSION;

    /** */
    private final GridKernalContext ctx;

    /** */
    private final GridProductVersion ver;

    /** */
    private final GridProductEdition edition;

    /** Update notifier. */
    private final GridUpdateNotifier verChecker;

    /**
     *
     */
    static {
        boolean ent0;

        try {
            ent0 = Class.forName("org.gridgain.grid.kernal.breadcrumb") != null;
        }
        catch (ClassNotFoundException ignored) {
            ent0 = false;
        }

        ENT = ent0;

        EDITION = GridProperties.get("gridgain.edition");
        VER = GridProperties.get("gridgain.version");
        BUILD = Long.valueOf(GridProperties.get("gridgain.build"));
        REV_HASH = GridProperties.get("gridgain.revision");
        RELEASE_DATE = GridProperties.get("gridgain.rel.date");

        VER_BYTES = U.intToBytes(VER.hashCode());

        COMPOUND_VERSION = EDITION + "-" + (ENT ? "ent" : "os") + "-" + VER;
    }

    /**
     * @param ctx Kernal context.
     * @param verChecker Update notifier.
     */
    public GridProductImpl(GridKernalContext ctx, GridUpdateNotifier verChecker) {
        this.ctx = ctx;
        this.verChecker = verChecker;

        String releaseType = ctx.isEnterprise() ? "ent" : "os";

        ver = GridProductVersion.fromString(EDITION + "-" + releaseType + "-" + VER + '-' + BUILD + '-' + REV_HASH);

        edition = editionFromString(EDITION);
    }

    /** {@inheritDoc} */
    @Override public GridProductEdition edition() {
        return edition;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridProductLicense license() {
        ctx.gateway().readLock();

        try {
            return ctx.license().license();
        }
        finally {
            ctx.gateway().readUnlock();
        }
    }

    /** {@inheritDoc} */
    @Override public void updateLicense(String lic) throws GridProductLicenseException {
        ctx.gateway().readLock();

        try {
            ctx.license().updateLicense(lic);
        }
        finally {
            ctx.gateway().readUnlock();
        }
    }

    /** {@inheritDoc} */
    @Override public GridProductVersion version() {
        return ver;
    }

    /** {@inheritDoc} */
    @Override public String copyright() {
        return COPYRIGHT;
    }

    /** {@inheritDoc} */
    @Nullable @Override public String latestVersion() {
        ctx.gateway().readLock();

        try {
            return verChecker != null ? verChecker.latestVersion() : null;
        }
        finally {
            ctx.gateway().readUnlock();
        }
    }

    /**
     * @param edition Edition name.
     * @return Edition.
     */
    private static GridProductEdition editionFromString(String edition) {
        switch (edition) {
            case "datagrid":
                return DATA_GRID;

            case "hadoop":
                return HADOOP;

            case "streaming":
                return STREAMING;

            case "mongo":
                return MONGO;

            case "platform":
                return PLATFORM;
        }

        throw new GridRuntimeException("Failed to determine GridGain edition: " + edition);
    }
}
