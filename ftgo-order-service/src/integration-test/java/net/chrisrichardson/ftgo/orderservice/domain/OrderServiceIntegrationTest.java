package net.chrisrichardson.ftgo.orderservice.domain;

import com.jayway.jsonpath.JsonPath;
import io.eventuate.tram.commands.common.ChannelMapping;
import io.eventuate.tram.commands.common.CommandMessageHeaders;
import io.eventuate.tram.commands.common.DefaultChannelMapping;
import io.eventuate.tram.commands.producer.TramCommandProducerConfiguration;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import io.eventuate.tram.inmemory.TramInMemoryConfiguration;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.testutil.TestMessageConsumerFactory;
import io.eventuate.util.test.async.Eventually;
import net.chrisrichardson.ftgo.common.Money;
import net.chrisrichardson.ftgo.consumerservice.api.ConsumerServiceChannels;
import net.chrisrichardson.ftgo.consumerservice.api.ValidateOrderByConsumer;
import net.chrisrichardson.ftgo.orderservice.messaging.OrderServiceMessagingConfiguration;
import net.chrisrichardson.ftgo.orderservice.service.OrderCommandHandlersConfiguration;
import net.chrisrichardson.ftgo.orderservice.web.MenuItemIdAndQuantity;
import net.chrisrichardson.ftgo.orderservice.web.OrderWebConfiguration;
import net.chrisrichardson.ftgo.restaurantservice.events.MenuItem;
import net.chrisrichardson.ftgo.restaurantservice.events.RestaurantCreated;
import net.chrisrichardson.ftgo.restaurantservice.events.RestaurantMenu;
import net.chrisrichardson.ftgo.testutil.FtgoTestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.function.Predicate;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OrderServiceIntegrationTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderServiceIntegrationTest {


  public static final String RESTAURANT_ID = "1";
  @Value("${local.server.port}")
  private int port;

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  @Configuration
  @EnableAutoConfiguration
  @Import({OrderWebConfiguration.class, OrderServiceMessagingConfiguration.class,  OrderCommandHandlersConfiguration.class,
          TramCommandProducerConfiguration.class,
          TramInMemoryConfiguration.class})
  public static class TestConfiguration {

    @Bean
    public ChannelMapping channelMapping() {
      return new DefaultChannelMapping.DefaultChannelMappingBuilder().build();
    }

    @Bean
    public TestMessageConsumerFactory testMessageConsumerFactory() {
      return new TestMessageConsumerFactory();
    }


    @Bean
    public DataSource dataSource() {
      EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
      return builder.setType(EmbeddedDatabaseType.H2)
              .addScript("eventuate-tram-embedded-schema.sql")
              .addScript("eventuate-tram-sagas-embedded.sql")
              .build();
    }


    @Bean
    public TestMessageConsumer2 mockConsumerService() {
      return new TestMessageConsumer2("mockConsumerService", ConsumerServiceChannels.consumerServiceChannel);
    }
  }

  @Autowired
  private DomainEventPublisher domainEventPublisher;

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private OrderService orderService;

  private static final String CHICKED_VINDALOO_MENU_ITEM_ID = "1";

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  @Qualifier("mockConsumerService")
  private TestMessageConsumer2  mockConsumerService;

  @Test
  public void shouldCreateOrder() {
    domainEventPublisher.publish("net.chrisrichardson.ftgo.restaurantservice.domain.Restaurant", RESTAURANT_ID,
            Collections.singletonList(new RestaurantCreated("Ajanta",
                    new RestaurantMenu(Collections.singletonList(new MenuItem(CHICKED_VINDALOO_MENU_ITEM_ID, "Chicken Vindaloo", new Money("12.34")))))));

    Eventually.eventually(() -> {
      FtgoTestUtil.assertPresent(restaurantRepository.findById(Long.parseLong(RESTAURANT_ID)));
    });

    long consumerId = 1511300065921L;

    Order order = orderService.createOrder(consumerId, Long.parseLong(RESTAURANT_ID), Collections.singletonList(new MenuItemIdAndQuantity(CHICKED_VINDALOO_MENU_ITEM_ID, 5)));

    FtgoTestUtil.assertPresent(orderRepository.findById(order.getId()));

    String expectedPayload = "{\"consumerId\":1511300065921,\"orderId\":1,\"orderTotal\":\"61.70\"}";

    Message message = mockConsumerService.assertMessageReceived(
            commandMessageOfType(ValidateOrderByConsumer.class.getName()).and(withPayload(expectedPayload)));

    System.out.println("message=" + message);

  }

  private Predicate<? super Message> withPayload(String expectedPayload) {
    return (m) -> expectedPayload.equals(m.getPayload());
  }

  private Predicate<Message> forConsumer(long consumerId) {
    return (m) -> {
      Object doc = com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().parse(m.getPayload());
      Object s = JsonPath.read(doc, "$.consumerId");
      return new Long(consumerId).equals(s);
    };
  }

  private Predicate<Message> commandMessageOfType(String commandType) {
    return (m) -> m.getRequiredHeader(CommandMessageHeaders.COMMAND_TYPE).equals(commandType);
  }

}