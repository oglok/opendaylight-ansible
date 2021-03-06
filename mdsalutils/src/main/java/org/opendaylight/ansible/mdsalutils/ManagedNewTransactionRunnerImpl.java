/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.ansible.mdsalutils;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.infrautils.utils.function.InterruptibleCheckedConsumer;
import org.opendaylight.infrautils.utils.function.InterruptibleCheckedFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ManagedNewTransactionRunner}.
 */
@Beta
// Do *NOT* mark this as @Singleton, because users choose Impl; and as long as this in API, because of https://wiki.opendaylight.org/view/BestPractices/DI_Guidelines#Nota_Bene
public class ManagedNewTransactionRunnerImpl implements ManagedNewTransactionRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ManagedNewTransactionRunnerImpl.class);

    private final DataBroker broker;

    @Inject
    public ManagedNewTransactionRunnerImpl(DataBroker broker) {
        this.broker = broker;
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public <E extends Exception> ListenableFuture<Void>
    callWithNewWriteOnlyTransactionAndSubmit(InterruptibleCheckedConsumer<WriteTransaction, E> txCnsmr) {
        WriteTransaction realTx = broker.newWriteOnlyTransaction();
        WriteTransaction wrappedTx = new NonSubmitCancelableWriteTransaction(realTx);
        try {
            txCnsmr.accept(wrappedTx);
            return realTx.submit();
            // catch Exception for both the <E extends Exception> thrown by accept() as well as any RuntimeException
        } catch (Exception e) {
            if (!realTx.cancel()) {
                LOG.error("Transaction.cancel() return false - this should never happen (here)");
            }
            return immediateFailedFuture(e);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public <D extends Datastore, E extends Exception> FluentFuture<Void>
    callWithNewWriteOnlyTransactionAndSubmit(Class<D> datastoreType,
                                             InterruptibleCheckedConsumer<TypedWriteTransaction<D>, E> txRunner) {
        WriteTransaction realTx = broker.newWriteOnlyTransaction();
        TypedWriteTransaction<D> wrappedTx =
            new TypedWriteTransactionImpl<>(datastoreType, realTx);
        try {
            txRunner.accept(wrappedTx);
            return realTx.commit().transform(commitInfo -> null, MoreExecutors.directExecutor());
            // catch Exception for both the <E extends Exception> thrown by accept() as well as any RuntimeException
        } catch (Exception e) {
            if (!realTx.cancel()) {
                LOG.error("Transaction.cancel() return false - this should never happen (here)");
            }
            return FluentFuture.from(immediateFailedFuture(e));
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public <E extends Exception> ListenableFuture<Void>
    callWithNewReadWriteTransactionAndSubmit(InterruptibleCheckedConsumer<ReadWriteTransaction, E> txRunner) {
        ReadWriteTransaction realTx = broker.newReadWriteTransaction();
        ReadWriteTransaction wrappedTx = new NonSubmitCancelableReadWriteTransaction(realTx);
        try {
            txRunner.accept(wrappedTx);
            return realTx.submit();
            // catch Exception for both the <E extends Exception> thrown by accept() as well as any RuntimeException
        } catch (Exception e) {
            if (!realTx.cancel()) {
                LOG.error("Transaction.cancel() returned false, which should never happen here");
            }
            return immediateFailedFuture(e);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public <D extends Datastore, E extends Exception> FluentFuture<Void>
    callWithNewReadWriteTransactionAndSubmit(Class<D> datastoreType,
                                             InterruptibleCheckedConsumer<TypedReadWriteTransaction<D>, E> txRunner) {
        ReadWriteTransaction realTx = broker.newReadWriteTransaction();
        TypedReadWriteTransaction<D> wrappedTx =
            new TypedReadWriteTransactionImpl<>(datastoreType, realTx);
        try {
            txRunner.accept(wrappedTx);
            return realTx.commit().transform(commitInfo -> null, MoreExecutors.directExecutor());
            // catch Exception for both the <E extends Exception> thrown by accept() as well as any RuntimeException
        } catch (Exception e) {
            if (!realTx.cancel()) {
                LOG.error("Transaction.cancel() returned false, which should never happen here");
            }
            return FluentFuture.from(immediateFailedFuture(e));
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public <D extends Datastore, E extends Exception, R> FluentFuture<R> applyWithNewReadWriteTransactionAndSubmit(
        Class<D> datastoreType, InterruptibleCheckedFunction<TypedReadWriteTransaction<D>, R, E> txRunner) {
        ReadWriteTransaction realTx = broker.newReadWriteTransaction();
        TypedReadWriteTransaction<D> wrappedTx =
            new TypedReadWriteTransactionImpl<>(datastoreType, realTx);
        try {
            R result = txRunner.apply(wrappedTx);
            return realTx.commit().transform(v -> result, MoreExecutors.directExecutor());
            // catch Exception for both the <E extends Exception> thrown by accept() as well as any RuntimeException
        } catch (Exception e) {
            if (!realTx.cancel()) {
                LOG.error("Transaction.cancel() returned false, which should never happen here");
            }
            return FluentFuture.from(immediateFailedFuture(e));
        }
    }
}
