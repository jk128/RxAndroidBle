package com.polidea.rxandroidble.internal;

import android.os.DeadObjectException;
import android.support.annotation.NonNull;

import com.polidea.rxandroidble.exceptions.BleException;
import com.polidea.rxandroidble.internal.operations.Operation;

import com.polidea.rxandroidble.internal.serialization.QueueReleaseInterface;
import com.polidea.rxandroidble.internal.util.QueueReleasingEmitterWrapper;

import rx.Emitter;
import rx.Observable;
import rx.functions.Action1;

/**
 * The base class for all operations that are executed on the Bluetooth Queue.
 * This class is intended to be a kind of wrapper over an Observable (returned by function
 * {@link RxBleRadioOperation#run(QueueReleaseInterface)}).
 *
 * Implements {@link Operation#run(QueueReleaseInterface)} interface which will be subscribed and unsubscribed on the application's
 * main thread.
 *
 * @param <T> What is returned from this operation onNext()
 */
public abstract class RxBleRadioOperation<T> implements Operation<T> {

    /**
     * A function that returns this operation as an Observable.
     * When the returned observable will be subscribed this operation will be scheduled
     * to be run on the main thread in future. When appropriate the call to run() will be executed.
     * This operation is expected to call release() at appropriate point after the run() was called.
     */
    @Override
    public final Observable<T> run(final QueueReleaseInterface queueReleaseInterface) {

        return Observable.create(
                new Action1<Emitter<T>>() {
                    @Override
                    public void call(Emitter<T> emitter) {
                        try {
                            protectedRun(emitter, queueReleaseInterface);
                        } catch (DeadObjectException deadObjectException) {
                            emitter.onError(provideException(deadObjectException));
                        } catch (Throwable throwable) {
                            emitter.onError(throwable);
                        }
                    }
                },
                Emitter.BackpressureMode.NONE
        );
    }

    /**
     * This method will be overridden in a concrete operation implementations and will contain specific operation logic.
     *
     * Implementations should call emitter methods to inform the outside world about emissions of `onNext()`/`onError()`/`onCompleted()`.
     * Implementations must call {@link QueueReleaseInterface#release()} at appropriate point to release the queue for any other operations
     * that are queued.
     *
     * If the emitter has been canceled it is response of the operation to call {@link QueueReleaseInterface#release()} when possible
     * subsequent operations will be able to start {@link android.bluetooth.BluetoothGatt} functions successfully. Check usage of
     * {@link QueueReleasingEmitterWrapper} for convenience.
     *
     * @param emitter the emitter to be called in order to inform the caller about the output of a particular run of the operation
     * @param queueReleaseInterface the queue release interface to release the queue when ready
     */
    protected abstract void protectedRun(Emitter<T> emitter, QueueReleaseInterface queueReleaseInterface) throws Throwable;

    /**
     * This function will be overriden in concrete operation implementations to provide an exception with needed context
     *
     * @param deadObjectException the cause for the exception
     */
    protected abstract BleException provideException(DeadObjectException deadObjectException);

    /**
     * A function returning the priority of this operation
     *
     * @return the priority of this operation
     */
    public Priority definedPriority() {
        return Priority.NORMAL;
    }

    /**
     * The function for determining which position in Bluetooth Radio's Priority Blocking Queue
     * this operation should take
     *
     * @param another another operation
     * @return the comparison result
     */
    @Override
    public int compareTo(@NonNull Operation another) {
        return another.definedPriority().priority - definedPriority().priority;
    }
}
