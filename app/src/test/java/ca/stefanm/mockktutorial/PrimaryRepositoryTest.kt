package ca.stefanm.mockktutorial

import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PrimaryRepositoryTest {


    @Test
    fun `flakey data returns a couple of Ok 1 items before failing`() = runTest {
        //Silly test, but it's here to prove the test harness works.


        val primaryRepository = PrimaryRepository(
            secondaryRepository = SecondaryRepository(
                tertiaryRepository = TertiaryRepository()
            )
        )

        primaryRepository.prepareFlakeyData().test {
            val item1 = awaitItem()
            val item2 = awaitItem()
            val item3 = awaitItem()
            val item4 = awaitItem()
            val item5 = awaitItem()
            assertEquals(listOf(item1, item2, item3, item4), listOf(
                PrimaryRepository.FlakeyFlowResult.Ok(1),
                PrimaryRepository.FlakeyFlowResult.Ok(1),
                PrimaryRepository.FlakeyFlowResult.Ok(1),
                PrimaryRepository.FlakeyFlowResult.Ok(1),
                //Actually, we should use a matcher for item 5
            ))
            assertTrue(item5 is PrimaryRepository.FlakeyFlowResult.Failed)
            item5 as PrimaryRepository.FlakeyFlowResult.Failed
            assertTrue(item5.cause is TertiaryRepository.FlakeyFlowException)

            awaitComplete()
        }
    }
}