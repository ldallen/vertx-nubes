package mock.fixtures;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mvc.fixtures.Fixture;
import mock.domains.Dog;
import mock.services.DogService;

public class DogFixture extends Fixture {

    private DogService dogs;

    @Override
    public int executionOrder() {
        return 1;
    }

    @Override
    public void startUp(Vertx vertx, Future<Void> future) {
        Dog snoopy = new Dog("Snoopy", "Beagle");
        Dog bill = new Dog("Bill", "Cocker");
        Dog rantanplan = new Dog("Rantanplan", "German shepherd");
        Dog milou = new Dog("Milou", "Fox terrier");
        Dog idefix = new Dog("Idefix", "Westy");
        Dog pluto = new Dog("Pluto", "Mutt");
        dogs.add(snoopy);
        dogs.add(bill);
        dogs.add(rantanplan);
        dogs.add(milou);
        dogs.add(idefix);
        dogs.add(pluto);
        future.complete();
    }

    @Override
    public void tearDown(Vertx vertx, Future<Void> future) {
        future.complete();
    }

}
