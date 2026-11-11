package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.NodeEventPrioritiesDao;
import org.artifactory.storage.db.event.entity.StoragePrioritizedNodeEvent;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.common.ClockUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class NodeEventPrioritiesDaoTest extends DbBaseTest {
    @Autowired
    private NodeEventPrioritiesDao dao;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes.sql");
    }

    public void testGetEventByPriority() throws SQLException {
        assertEquals(dao.getEventsByPriorityAndOperatorId(2, "metadata-dispatcher").size(), 2);
        assertEquals(dao.getEventsByPriorityAndOperatorId(3, "metadata-dispatcher").size(), 2);
    }

    public void testInsertEventPriority() throws SQLException {
        dao.insertEventPriority(getNodeEventPriority(111222333444L, null));
        assertEquals(dao.getEventsByPriorityAndOperatorId(4, "metadata-dispatcher").size(), 1);
        dao.deleteEventPriorityById(111222333444L);
    }

    public void testInsertEventPriorityWith1344PathLength() throws SQLException {
        String path = """
                Loremipsumdolorsitamet,consecteturadipiscingelit.Sedquissapinarcu.Mauristincidunt,liberovelsollicitudin
                fringilla,magnaleodictumnisi,necaliquetelitorciidurna.Phaselluslacinialeoetestfringilla,velfaucibusleoti
                ncidunt.Vivamusinhendreritnunc,egetlobortiselit.Fuscesitametipsumutligulatempustempus.Integernecexeujust
                ofringillarutrum.Duispulvinardolormagna,acvariusenimmalesuadased.Sedefficiturtortorvitaellacvariuser,a11
                tristiqueenimgravida.Crasiddiamsapien.Utfringillanullaidmaximusaliquam,nequeorciultriciesest,vitaedapibu
                sturpisurnavitaeligula.Cras pulvinarelitnecco feugiat,ipsumexconguedui, aconvallisdolorestsitametvelit.P
                ellentesqueinexacloremconvallisconsectetur.Integerdictumnullavitae vestibulu m pellentesque.Fusceacinter
                dumlacus.Sedegettellusnonligulamalesuadabibendum.Nullamvelvestibulumnisl.Vivamusatpurusegetliberoaliquet
                lobortisidvitaenisi.Curabiturvehiculaurnaeujustoaliquam,sedposuereultrices.Aliquammenenatismagnaacmetusg
                ravida,velbibendummetusefficitur.Quisquepulvinarfaucibusrises,sedegestasipsumcongueac.Etiamaliquamsitame
                tturpisacfinibus.Donecsitametnisialectusultricesultrices.Integerauctorposuereerat,inlaoreetduiullamcorpe
                tturpisacfinibus.Donecsitametnisialectusultricesultrices.Integerauctorposuereerat,inlaoreetduiullamcorpe
                rat.Utvelloremsedfelistempushendrerit.Pellentesquesitametaugueatleotinciduntlacinia.Maurisvelconsectetur
                arcu.Nullamveltellusquisipsumdictumviverrasitametacvelit.Nullafacilisi.Donecacsapiensitametdolorl1231234
                """;
        path = path.substring(0, 1344);
        dao.insertEventPriority(getNodeEventPriority(111222333444L, path));
        assertEquals(dao.getEventsByPriorityAndOperatorId(4, "metadata-dispatcher").size(), 1);
        dao.deleteEventPriorityById(111222333444L);
    }

    @Test(expectedExceptions = SQLException.class, expectedExceptionsMessageRegExp =
            "ERROR: value too long for type character varying\\(1344\\)" +
                    "|.* Data too long for column 'path' at row 1+" +
                    "|String or binary data would be truncated.*" +
                    "|.*\"PATH\" \\(actual: 1345, maximum: 1344\\).*" +
                    "|A truncation error was encountered trying to shrink VARCHAR .*")
    public void testInsertEventPriorityWith1345PathLength() throws SQLException {
        String path = """
                Loremipsumdolorsitamet,consecteturadipiscingelit.Sedquissapinarcu.Mauristincidunt,liberovelsollicitudin
                fringilla,magnaleodictumnisi,necaliquetelitorciidurna.Phaselluslacinialeoetestfringilla,velfaucibusleoti
                ncidunt.Vivamusinhendreritnunc,egetlobortiselit.Fuscesitametipsumutligulatempustempus.Integernecexeujust
                ofringillarutrum.Duispulvinardolormagna,acvariusenimmalesuadased.Sedefficiturtortorvitaellacvariuser,a11
                tristiqueenimgravida.Crasiddiamsapien.Utfringillanullaidmaximusaliquam,nequeorciultriciesest,vitaedapibu
                sturpisurnavitaeligula.Cras pulvinarelitnecco feugiat,ipsumexconguedui, aconvallisdolorestsitametvelit.P
                ellentesqueinexacloremconvallisconsectetur.Integerdictumnullavitae vestibulu m pellentesque.Fusceacinter
                dumlacus.Sedegettellusnonligulamalesuadabibendum.Nullamvelvestibulumnisl.Vivamusatpurusegetliberoaliquet
                lobortisidvitaenisi.Curabiturvehiculaurnaeujustoaliquam,sedposuereultrices.Aliquammenenatismagnaacmetusg
                ravida,velbibendummetusefficitur.Quisquepulvinarfaucibusrises,sedegestasipsumcongueac.Etiamaliquamsitame
                ravida,velbibendummetusefficitur.Quisquepulvinarfaucibusrises,sedegestasipsumcongueac.Etiamaliquamsitame
                tturpisacfinibus.Donecsitametnisialectusultricesultrices.Integerauctorposuereerat,inlaoreetduiullamcorpe
                rat.Utvelloremsedfelistempushendrerit.Pellentesquesitametaugueatleotinciduntlacinia.Maurisvelconsectetur
                arcu.Nullamveltellusquisipsumdictumviverrasitametacvelit.Nullafacilisi.Donecacsapiensitametdolorl1231234
                """;
        path = path.substring(0, 1345);
        dao.insertEventPriority(getNodeEventPriority(111222333444L, path));
    }

    public void deleteEventPriorityById() throws SQLException {
        dao.insertEventPriority(getNodeEventPriority(111222333446L, null));
        dao.deleteEventPriorityById(111222333446L);
        assertFalse(dao.findPriorityById(111222333446L).isPresent());
    }

    @Test(expectedExceptions = SQLException.class)
    public void testPrimaryKeyConstraintViolated() throws SQLException {
        dao.insertEventPriority(getNodeEventPriority(111222333445L, null));
        dao.insertEventPriority(getNodeEventPriority(111222333445L, null));
    }

    public void testCountEventsByOperatorId() throws SQLException {
        assertTrue(dao.countRetryableEventsByOperatorId("metadata-dispatcher").isPresent());
        assertEquals(dao.countRetryableEventsByOperatorId("metadata-dispatcher").get().intValue(), 3);
    }

    public void testGetMinimalPriorityByOperatorId() throws SQLException {
        assertTrue(dao.getMinimalPriorityByOperatorId("metadata-dispatcher").isPresent());
        assertEquals(dao.getMinimalPriorityByOperatorId("metadata-dispatcher").get().intValue(), 2);
        StoragePrioritizedNodeEvent nodeEventPriority = getNodeEventPriority(111222333445L, null);
        nodeEventPriority.setPriority(1);
        nodeEventPriority.setPriorityId(1234567891L);
        dao.insertEventPriority(nodeEventPriority);
        assertEquals(dao.getMinimalPriorityByOperatorId("metadata-dispatcher").get().intValue(), 1);
        dao.deleteAll();
        // no values -> minimal priority is 1
        assertTrue(dao.getAll().isEmpty());
        assertEquals(dao.getMinimalPriorityByOperatorId("metadata-dispatcher").get().intValue(), 0);
    }

    public void testFindPriorityById() throws SQLException {
        dao.insertEventPriority(getNodeEventPriority(111222333446L, null));
        assertTrue(dao.findPriorityById(getNodeEventPriority(111222333446L, null).getPriorityId()).isPresent());
    }

    private StoragePrioritizedNodeEvent getNodeEventPriority(long priorityId, String path) {
        if (path == null) {
            path = "a/b/c";
        }
        return new StoragePrioritizedNodeEvent(priorityId, path, "create", "metadata-dispatcher", 4,
                ClockUtils.epochMillis(), 0);
    }
}
