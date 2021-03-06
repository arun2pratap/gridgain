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

package org.gridgain.grid.kernal.processors.cache.distributed.dht.colocated;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.kernal.processors.cache.*;
import org.gridgain.grid.kernal.processors.cache.distributed.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.grid.util.future.*;
import org.gridgain.grid.util.tostring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.gridgain.grid.cache.GridCacheTxState.*;

/**
 * Colocated tx finish future.
 */
public class GridDhtColocatedTxFinishFuture<K, V> extends GridCompoundIdentityFuture<GridCacheTx>
    implements GridCacheFuture<GridCacheTx> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Logger reference. */
    private static final AtomicReference<GridLogger> logRef = new AtomicReference<>();

    /** Context. */
    private GridCacheContext<K, V> cctx;

    /** Future ID. */
    private GridUuid futId;

    /** Transaction. */
    @GridToStringExclude
    private GridDhtColocatedTxLocal<K, V> tx;

    /** Logger. */
    private GridLogger log;

    /** Error. */
    private AtomicReference<Throwable> err = new AtomicReference<>(null);

    /** Node mappings. */
    private ConcurrentMap<UUID, GridDistributedTxMapping<K, V>> mappings;

    /** Trackable flag. */
    private boolean trackable = true;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtColocatedTxFinishFuture() {
        // No-op.
    }

    /**
     * @param cctx Context.
     * @param tx Transaction.
     */
    public GridDhtColocatedTxFinishFuture(GridCacheContext<K, V> cctx, GridDhtColocatedTxLocal<K, V> tx) {
        super(cctx.kernalContext(), F.<GridCacheTx>identityReducer(tx));

        assert cctx != null;

        this.cctx = cctx;
        this.tx = tx;

        mappings = tx.mappings();

        futId = GridUuid.randomUuid();

        log = U.logger(ctx, logRef, GridDhtColocatedTxFinishFuture.class);
    }

    /** {@inheritDoc} */
    @Override public GridUuid futureId() {
        return futId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion version() {
        return tx.xidVersion();
    }

    /**
     * @return Involved nodes.
     */
    @Override public Collection<? extends GridNode> nodes() {
        return F.viewReadOnly(futures(), new GridClosure<GridFuture<?>, GridNode>() {
            @Nullable @Override public GridNode apply(GridFuture<?> f) {
                if (isMini(f))
                    return ((MiniFuture)f).node();

                return cctx.discovery().localNode();
            }
        });
    }

    /** {@inheritDoc} */
    @Override public boolean onNodeLeft(UUID nodeId) {
        for (GridFuture<?> fut : futures())
            if (isMini(fut)) {
                MiniFuture f = (MiniFuture)fut;

                if (f.node().id().equals(nodeId)) {
                    // Remove previous mapping.
                    mappings.remove(nodeId);

                    f.onResult(new GridTopologyException("Remote node left grid (will fail): " + nodeId));

                    return true;
                }
            }

        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean trackable() {
        return trackable;
    }

    /**
     * Marks this future as not trackable.
     */
    @Override public void markNotTrackable() {
        trackable = false;
    }

    /**
     * @param e Error.
     */
    void onError(Throwable e) {
        tx.commitError(e);

        if (err.compareAndSet(null, e)) {
            boolean marked = tx.setRollbackOnly();

            if (e instanceof GridCacheTxRollbackException) {
                if (marked) {
                    try {
                        tx.rollback();
                    }
                    catch (GridException ex) {
                        U.error(log, "Failed to automatically rollback transaction: " + tx, ex);
                    }
                }
            }
            else if (tx.implicit() && tx.isSystemInvalidate()) { // Finish implicit transaction on heuristic error.
                try {
                    tx.close();
                }
                catch (GridException ex) {
                    U.error(log, "Failed to invalidate transaction: " + tx, ex);
                }
            }

            onComplete();
        }
    }

    /**
     * @param nodeId Sender.
     * @param res Result.
     */
    void onResult(UUID nodeId, GridNearTxFinishResponse<K, V> res) {
        if (!isDone())
            for (GridFuture<GridCacheTx> fut : futures()) {
                if (isMini(fut)) {
                    MiniFuture f = (MiniFuture)fut;

                    if (f.futureId().equals(res.miniId())) {
                        assert f.node().id().equals(nodeId);

                        f.onResult(res);
                    }
                }
            }
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(GridCacheTx tx, Throwable err) {
        if (initialized() || err != null) {
            if (this.tx.onePhaseCommit() && (this.tx.state() == COMMITTING))
                this.tx.tmCommit();

            Throwable e = this.err.get();

            if (super.onDone(tx, e != null ? e : err)) {
                // Don't forget to clean up.
                cctx.mvcc().removeFuture(this);

                return true;
            }
        }

        return false;
    }

    /**
     * @param f Future.
     * @return {@code True} if mini-future.
     */
    private boolean isMini(GridFuture<?> f) {
        return f.getClass().equals(MiniFuture.class);
    }

    /**
     * Completeness callback.
     */
    private void onComplete() {
        onDone(tx, err.get());
    }

    /**
     * @param commit {@code True} if commit.
     * @return Synchronous flag.
     */
    private boolean isSync(boolean commit) {
        return commit ? tx.syncCommit() : tx.syncRollback();
    }

    /**
     * @param commit {@code True} if commit.
     */
    void finish(boolean commit) {
        if (tx.onePhaseCommit()) {
            // No need to send messages as transaction was already committed on remote node.
            // Finish local mapping only as we need send commit message to backups.
            for (GridDistributedTxMapping<K, V> m : mappings.values()) {
                if (m.node().isLocal()) {
                    GridFuture<GridCacheTx> fut = cctx.colocated().finishLocal(commit, m.explicitLock(), tx);

                    // Add new future.
                    if (fut != null)
                        add(fut);
                }
            }

            markInitialized();

            return;
        }

        if (mappings != null) {
            finish(mappings.values(), commit);

            markInitialized();

            if (!isSync(commit)) {
                boolean complete = true;

                for (GridFuture<?> f : pending())
                    // Mini-future in non-sync mode gets done when message gets sent.
                    if (isMini(f) && !f.isDone())
                        complete = false;

                if (complete)
                    onComplete();
            }
        }
        else {
            assert !commit;

            try {
                tx.rollback();
            }
            catch (GridException e) {
                U.error(log, "Failed to rollback empty transaction: " + tx, e);
            }

            markInitialized();
        }
    }

    /**
     * @param mappings Mappings.
     * @param commit {@code True} to commit.
     */
    private void finish(Iterable<GridDistributedTxMapping<K, V>> mappings, boolean commit) {
        // Create mini futures.
        for (GridDistributedTxMapping<K, V> m : mappings)
            finish(m, commit);
    }

    /**
     * @param m Mapping.
     * @param commit {@code True} to commit.
     */
    private void finish(GridDistributedTxMapping<K, V> m, boolean commit) {
        GridNode n = m.node();

        assert !m.empty();

        // If this is the primary node for the keys.
        if (n.isLocal()) {
            GridFuture<GridCacheTx> fut = cctx.colocated().finishLocal(commit, m.explicitLock(), tx);

            // Add new future.
            if (fut != null)
                add(fut);
        }
        else {
            GridNearTxFinishRequest<K, V> req = new GridNearTxFinishRequest<>(
                futId,
                tx.xidVersion(),
                tx.threadId(),
                commit,
                tx.isInvalidate(),
                m.explicitLock(),
                tx.topologyVersion(),
                null,
                null,
                null,
                tx.size(),
                commit && tx.pessimistic() ? m.writes() : null,
                commit && tx.pessimistic() ? F.view(tx.writeEntries(), CU.<K, V>transferRequired()) : null,
                commit ? tx.syncCommit() : tx.syncRollback()
            );

            MiniFuture fut = new MiniFuture(m);

            req.miniId(fut.futureId());

            add(fut); // Append new future.

            if (tx.pessimistic())
                cctx.tm().beforeFinishRemote(n.id(), tx.threadId());

            try {
                cctx.io().send(n, req);

                // If we don't wait for result, then mark future as done.
                if (!isSync(commit) && !m.explicitLock())
                    fut.onDone();
            }
            catch (GridTopologyException e) {
                // Remove previous mapping.
                mappings.remove(m.node().id());

                fut.onResult(e);
            }
            catch (GridException e) {
                // Fail the whole thing.
                fut.onResult(e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtColocatedTxFinishFuture.class, this, super.toString());
    }

    /**
     * Mini-future for get operations. Mini-futures are only waiting on a single
     * node as opposed to multiple nodes.
     */
    private class MiniFuture extends GridFutureAdapter<GridCacheTx> {
        /** */
        private static final long serialVersionUID = 0L;

        /** */
        private final GridUuid futId = GridUuid.randomUuid();

        /** Keys. */
        @GridToStringInclude
        private GridDistributedTxMapping<K, V> m;

        /**
         * Empty constructor required for {@link Externalizable}.
         */
        public MiniFuture() {
            // No-op.
        }

        /**
         * @param m Mapping.
         */
        MiniFuture(GridDistributedTxMapping<K, V> m) {
            super(cctx.kernalContext());

            this.m = m;
        }

        /**
         * @return Future ID.
         */
        GridUuid futureId() {
            return futId;
        }

        /**
         * @return Node ID.
         */
        public GridNode node() {
            return m.node();
        }

        /**
         * @return Keys.
         */
        public GridDistributedTxMapping<K, V> mapping() {
            return m;
        }

        /**
         * @param e Error.
         */
        void onResult(Throwable e) {
            if (log.isDebugEnabled())
                log.debug("Failed to get future result [fut=" + this + ", err=" + e + ']');

            // Fail.
            onDone(e);
        }

        /**
         * @param e Node failure.
         */
        void onResult(GridTopologyException e) {
            if (log.isDebugEnabled())
                log.debug("Remote node left grid while sending or waiting for reply (will ignore): " + this);

            // Complete future with tx.
            onDone(tx);
        }

        /**
         * @param res Result callback.
         */
        void onResult(GridNearTxFinishResponse<K, V> res) {
            if (res.error() != null)
                onDone(res.error());
            else
                onDone(tx);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(MiniFuture.class, this, "done", isDone(), "cancelled", isCancelled(), "err", error());
        }
    }
}
