package com.easternedgerobotics.rov.event;

import rx.Observable;

public interface EventPublisher {
    /**
     * Broadcasts the given value to the network.
     *
     * @param value the value to emit
     */
    void emit(final Object value);

    /**
     * Returns an {@link rx.Observable} of the values of the given type.
     *
     * @param clazz the class type to filter values by
     * @return an Observable that emits each value of the given type
     */
    <T> Observable<T> valuesOfType(final Class<T> clazz);

    /**
     * Stops receiving and broadcasting events.
     */
    void stop();

    /**
     * Block until this event publisher completes.
     */
    void await() throws InterruptedException;
}
