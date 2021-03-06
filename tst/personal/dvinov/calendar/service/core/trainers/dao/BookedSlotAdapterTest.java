package personal.dvinov.calendar.service.core.trainers.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static personal.dvinov.calendar.service.fixture.TestUtils.createDynamoTable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;

import personal.dvinov.calendar.service.core.trainers.business.SlotBusinessObject;

public class BookedSlotAdapterTest {
    private static final String TABLE_NAME = "BookedSlots";
    
    private static final String TRAINER_ID = "trainerId";
    private static final String CLIENT_ID = "clientId";
    
    private static final ZoneId TIME_ZONE = ZoneId.of("America/Los_Angeles");
    private static final Instant SEARCH_INTERVAL_START_TIME =       Instant.parse("2015-08-29T10:15:30Z");
    private static final Instant SEARCH_INTERVAL_END_TIME =         Instant.parse("2015-08-30T10:15:30Z");
    private static final Instant SLOT_START_TIME_WITHIN_INTERVAL =  Instant.parse("2015-08-30T09:00:30Z");
    private static final Instant SLOT_END_TIME_WITHIN_INTERVAL =    Instant.parse("2015-08-30T10:00:30Z");
    
    private static DynamoDBMapper mapper;
    private static BookedSlotAdapter adapter;
    private static AmazonDynamoDBClient client;
    
    @BeforeClass
    public static void setUpSuite() {
        client = new AmazonDynamoDBClient()
                // http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Tools.DynamoDBLocal.html
                .withEndpoint("http://localhost:8010");
        
        mapper = new DynamoDBMapper(client);
        adapter = new BookedSlotAdapter(mapper);
    }
    
    @Before
    public void setUpTest() {
        createDynamoTable(client, mapper, TABLE_NAME, BookedSlotDao.class);
    }
    
    @Test
    public void afterNoInsertionsListReturnsEmptyList() {
        final List<SlotBusinessObject> result = adapter.listBookedSlots(
                TRAINER_ID, SEARCH_INTERVAL_START_TIME, SEARCH_INTERVAL_END_TIME, TIME_ZONE);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void afterInsertionWithinIntervalListReturnsSlot() {
        insertSlot(SLOT_START_TIME_WITHIN_INTERVAL, SLOT_END_TIME_WITHIN_INTERVAL, "2015-08-30-01");

        final List<SlotBusinessObject> result = adapter.listBookedSlots(
                TRAINER_ID, SEARCH_INTERVAL_START_TIME, SEARCH_INTERVAL_END_TIME, TIME_ZONE);
        
        assertEquals(1, result.size());
        final SlotBusinessObject expectedSlot =
                new SlotBusinessObject(SLOT_START_TIME_WITHIN_INTERVAL, SLOT_END_TIME_WITHIN_INTERVAL, 1);
        assertEquals(expectedSlot, result.get(0));
    }
    
    @Test
    public void afterInsertionOutsideIntervalListReturnsNoSlots() {
        insertSlot(
                Instant.parse("2015-08-31T09:00:30Z"),
                Instant.parse("2015-08-31T10:00:30Z"),
                "2015-08-30-01");

        final List<SlotBusinessObject> result =
                adapter.listBookedSlots(TRAINER_ID, SEARCH_INTERVAL_START_TIME, SEARCH_INTERVAL_END_TIME, TIME_ZONE);
        
        assertTrue(result.isEmpty());
    }
    
    @Test(expected = IllegalStateException.class)
    public void afterInsertionWithMalformedDayPlusSlotThrowsIllegalStateException() {
        insertSlot(SLOT_START_TIME_WITHIN_INTERVAL, SLOT_END_TIME_WITHIN_INTERVAL, "2015-08-30-01BLAH");
        
        adapter.listBookedSlots(TRAINER_ID, SEARCH_INTERVAL_START_TIME, SEARCH_INTERVAL_END_TIME, TIME_ZONE);
    }
    
    @Test
    public void bookedSlotStoresSlot() {
        adapter.bookSlot(TRAINER_ID, CLIENT_ID, 2015, 9, 9, 0, TIME_ZONE);
        
        final LocalDateTime startTime = LocalDateTime.of(2015, 9, 9, 9, 0);
        final LocalDateTime endTime = LocalDateTime.of(2015, 9, 9, 10, 0);
        final BookedSlotDao expected = new BookedSlotDao(TRAINER_ID, "2015-09-09-00", CLIENT_ID,
                Date.from(startTime.atZone(TIME_ZONE).toInstant()),
                Date.from(endTime.atZone(TIME_ZONE).toInstant()));
        
        final BookedSlotDao fromDynamo = mapper.load(expected);
        assertTrue(fromDynamo != null);
    }
    
    @Test
    public void bookingSlotTwiceStoresSlotIdempotently() {
        adapter.bookSlot(TRAINER_ID, CLIENT_ID, 2015, 9, 9, 0, TIME_ZONE);
        adapter.bookSlot(TRAINER_ID, CLIENT_ID, 2015, 9, 9, 0, TIME_ZONE);
        
        final LocalDateTime startTime = LocalDateTime.of(2015, 9, 9, 9, 0);
        final LocalDateTime endTime = LocalDateTime.of(2015, 9, 9, 10, 0);
        final BookedSlotDao expected = new BookedSlotDao(TRAINER_ID, "2015-09-09-00", CLIENT_ID,
                Date.from(startTime.atZone(TIME_ZONE).toInstant()),
                Date.from(endTime.atZone(TIME_ZONE).toInstant()));
        
        final BookedSlotDao fromDynamo = mapper.load(expected);
        assertTrue(fromDynamo != null);
    }
    
    @Test(expected = ConditionalCheckFailedException.class)
    public void overwritingSlotWithNewClientThrowsConditionalCheckFailedException() {
        adapter.bookSlot(TRAINER_ID, CLIENT_ID, 2015, 9, 9, 0, TIME_ZONE);
        adapter.bookSlot(TRAINER_ID, "NEW_CLIENT_ID", 2015, 9, 9, 0, TIME_ZONE);
    }
    
    /**
     * Insert slot into Dynamo and return it
     * 
     * @param slotStart
     * @param slotEnd
     * @param dayPlusSlot
     * @return
     */
    private BookedSlotDao insertSlot(final Instant slotStart,
                                    final Instant slotEnd,
                                    final String dayPlusSlot) {
        
        final BookedSlotDao slot = new BookedSlotDao(
                TRAINER_ID, dayPlusSlot, CLIENT_ID, Date.from(slotStart), Date.from(slotEnd));
        
        mapper.save(slot);
        
        return slot;
    }
}