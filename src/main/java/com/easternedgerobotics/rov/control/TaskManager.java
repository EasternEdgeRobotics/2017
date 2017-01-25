package com.easternedgerobotics.rov.control;

import kotlin.Pair;
import org.pmw.tinylog.Logger;
import rx.Observable;
import rx.Scheduler;
import rx.subscriptions.CompositeSubscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TaskManager {
    private final Scheduler clock;

    private final Observable<Long> interval;

    private final List<Runnable> initialStateSetters = new ArrayList<>();

    private final CompositeSubscription subscriptions = new CompositeSubscription();

    private volatile boolean isStarted;

    public TaskManager(final long sleepDuration, final TimeUnit unit, final Scheduler clock) {
        this.clock = clock;
        this.interval = Observable.interval(sleepDuration, unit, clock);
    }

    public void start() {
        isStarted = true;
    }

    public void stop() {
        isStarted = false;
        clock.createWorker().schedule(() -> initialStateSetters.forEach(Runnable::run));
    }

    public void unsubscribe() {
        stop();
        subscriptions.unsubscribe();
    }

    public <S> TaskManager manage(
        final Supplier<S> supplier,
        final Consumer<S> consumer
    ) {
        subscriptions.add(interval.startWith(0L)
            .filter(t -> isStarted)
            .map(t -> supplier.get())
            .doOnError(Logger::warn)
            .subscribe(consumer::accept));
        return this;
    }

    public <C, O extends C> TaskManager manage(
        final Observable<O> source,
        final O initial,
        final Consumer<C> consumer
    ) {
        initialStateSetters.add(() -> consumer.accept(initial));
        subscriptions.add(interval.startWith(0L)
            .withLatestFrom(source.startWith(initial), (t, v) -> v)
            .filter(v -> isStarted)
            .doOnError(Logger::warn)
            .subscribe(consumer::accept));
        return this;
    }

    public <C1, O1 extends C1, C2, O2 extends C2> TaskManager manage(
        final Observable<O1> source1, final O1 initial1,
        final Observable<O2> source2, final O2 initial2,
        final BiConsumer<C1, C2> consumer
    ) {
        initialStateSetters.add(() -> consumer.accept(initial1, initial2));
        subscriptions.add(interval.startWith(0L)
            .withLatestFrom(
                Observable.combineLatest(source1.startWith(initial1), source2.startWith(initial2), Pair::new),
                (t, v) -> v)
            .filter(v -> isStarted)
            .subscribe(p -> consumer.accept(p.getFirst(), p.getSecond())));
        return this;
    }
}
