package io.smallrye.mutiny.operators;

import static io.smallrye.mutiny.helpers.ParameterValidation.SUPPLIER_PRODUCED_NULL;
import static io.smallrye.mutiny.helpers.ParameterValidation.nonNull;

import java.util.function.Function;
import java.util.function.Predicate;

import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniSubscriber;

public class UniOnFailureTransform<I, O> extends UniOperator<I, O> {

    private final Function<? super Throwable, ? extends Throwable> mapper;
    private final Predicate<? super Throwable> predicate;

    public UniOnFailureTransform(Uni<I> upstream,
            Predicate<? super Throwable> predicate,
            Function<? super Throwable, ? extends Throwable> mapper) {
        super(nonNull(upstream, "upstream"));
        this.mapper = nonNull(mapper, "mapper");
        this.predicate = nonNull(predicate, "predicate");
    }

    @Override
    protected void subscribing(UniSubscriber<? super O> subscriber) {
        AbstractUni.subscribe(upstream(), new UniDelegatingSubscriber<I, O>(subscriber) {

            @Override
            public void onFailure(Throwable failure) {
                boolean test;
                try {
                    test = predicate.test(failure);
                } catch (RuntimeException e) {
                    subscriber.onFailure(new CompositeException(failure, e));
                    return;
                }

                if (test) {
                    Throwable outcome;
                    try {
                        outcome = mapper.apply(failure);
                        // We cannot call onFailure here, as if onFailure would throw an exception
                        // it would be caught and onFailure would be called. This would be illegal.
                    } catch (Throwable e) {
                        subscriber.onFailure(e);
                        return;
                    }
                    if (outcome == null) {
                        subscriber.onFailure(new NullPointerException(SUPPLIER_PRODUCED_NULL));
                    } else {
                        subscriber.onFailure(outcome);
                    }
                } else {
                    subscriber.onFailure(failure);
                }
            }

        });
    }
}
