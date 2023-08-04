package ca.stefanm.mockktutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import ca.stefanm.mockktutorial.ui.theme.MockKTutorialTheme
import dagger.hilt.EntryPoint
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var primaryRepository: PrimaryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MockKTutorialTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(primaryRepository = primaryRepository)
                }
            }
        }
    }
}

@Composable
fun DataDisplay(
    oneShotValue : String,
    coldFlowValue : Int,
    coldFlowWithStartValue : Int,
    flakeyResultValue : PrimaryRepository.FlakeyFlowResult,
) {
    Column {
        Text(text = "OneShotValue: $oneShotValue")
        Text(text = "ColdFlowValue: $coldFlowValue")
        Text(text = "ColdFlowWithStartValue: $coldFlowWithStartValue")
        Text(text = "FlakeyResultValue: ${flakeyResultValue.toString()}")
    }
}

@Composable
fun DataButtons(
    onCallOneShot : () -> Unit = {},
    onCallPrepareFlakeyData : () -> Unit = {},
    onColdFlow : () -> Unit = {},
    onColdFlowWithStart : (start : Int) -> Unit = {},
    onCancelFlows : () -> Unit = {}
) {

    Card {
        Button(onClick = { onCallOneShot() }) {
            Text("Call one-shot")
        }

        Button(onClick = { onCallPrepareFlakeyData() }) {
            Text("Prepare Flakey Data")
        }

        Button(onClick = { onColdFlow() }) {
            Text("Cold Flow")
        }

        val start = remember { mutableStateOf(0) }
        Text(text = "Cold Flow With Start: ${start.value}")

        Row {
            Button(onClick = { start.value = start.value - 1 }) { Text(text = "Start --") }
            Button(onClick = { start.value = 0 }) { Text(text = "Start = 0") }
            Button(onClick = { start.value = start.value + 1 }) { Text(text = "Start ++") }
        }

        Button(onClick = { onColdFlowWithStart(start.value) }) {
            Text(text = "Cold flow with start")
        }

        Button(onClick = { onCancelFlows() }) {
            Text("Cancel flows")
        }
    }
}

@Composable
fun MainScreen(
    primaryRepository : PrimaryRepository
) {

    Column {

        val startVal = remember { mutableStateOf(0) }
        val coldFlowWithStartValue = produceState(initialValue = -1, producer = {
            primaryRepository.coldFlowWithStart(startVal.value).collect { value = it }
        }, key1 = startVal.value)

        val coldFlowValueRestartToggler = remember { mutableStateOf(true) }
        val coldFlowValue = produceState(initialValue = -1, producer = {
           primaryRepository.coldFlow().collect { value = it }
        }, key1 = coldFlowValueRestartToggler.value)

        val flakeyResultValueRestartToggler = remember { mutableStateOf(true) }
        val flakeyResultValue = produceState(initialValue = PrimaryRepository.FlakeyFlowResult.NoData as PrimaryRepository.FlakeyFlowResult, producer = {
            primaryRepository.prepareFlakeyData().collect { value = it }
        }, key1 = flakeyResultValueRestartToggler.value)

        
        val scope = rememberCoroutineScope()
        val oneShotToggler = remember { mutableStateOf(false) }
        val oneShotState = remember {
            mutableStateOf("noOneShot")
        }
        LaunchedEffect(oneShotToggler.value) {
            oneShotState.value = scope.async { primaryRepository.oneShot() }.await()
        }

        DataButtons(
            onCallOneShot = {oneShotToggler.value = !oneShotToggler.value },
            onColdFlow = { coldFlowValueRestartToggler.value = !coldFlowValueRestartToggler.value },
            onColdFlowWithStart = { start -> startVal.value = start },
            onCallPrepareFlakeyData = { flakeyResultValueRestartToggler.value = !flakeyResultValueRestartToggler.value }
        )

        DataDisplay(
            oneShotValue = oneShotState.value,
            coldFlowValue = coldFlowValue.value,
            coldFlowWithStartValue = coldFlowWithStartValue.value,
            flakeyResultValue = flakeyResultValue.value
        )
    }

}



class PrimaryRepository @Inject constructor(
    private val secondaryRepository : SecondaryRepository
) {

    sealed interface FlakeyFlowResult {
        data class Ok(val value : Int) : FlakeyFlowResult
        object NoData : FlakeyFlowResult
        data class Failed(val cause : Throwable) : FlakeyFlowResult
    }


    fun prepareFlakeyData() : Flow<FlakeyFlowResult> {
        return secondaryRepository.flakeyFlow()
            .map { FlakeyFlowResult.Ok(it) as FlakeyFlowResult }
            .retryWhen { cause, attempt ->

                //TODO in the session, let's try making this flow keep going when there's
                //TODO a failure. But first, write a test to show that we actually get three OK(1) emissions
                cause is TertiaryRepository.FlakeyFlowException && attempt < 3

            }
            .catch {
                if (it is TertiaryRepository.FlakeyFlowException) {
                    emit(FlakeyFlowResult.Failed(it))
                }
            }
    }

    //We can test with a real secondary repository, or we can go nuts and mock it very deeply.
    suspend fun oneShot() = secondaryRepository.oneShot()

    fun coldFlow() = secondaryRepository.coldFlow()

    fun coldFlowWithStart(start : Int) : Flow<Int> = secondaryRepository.coldFlowWithStart(start)
}

class SecondaryRepository @Inject constructor(
    private val tertiaryRepository: TertiaryRepository
) {

    suspend fun oneShot() : String = tertiaryRepository.oneShot()

    fun coldFlow() = tertiaryRepository.coldFlow()

    fun flakeyFlow() : Flow<Int> {
        return tertiaryRepository.makeFlakey(coldFlow()) { it.rem(2) == 0 }
    }

    //We can test we call the function correctly. (Argument Captor)
    fun coldFlowWithStart(start : Int) : Flow<Int> = tertiaryRepository.coldFlowWithStart(start)

}

class TertiaryRepository @Inject constructor() {

    @VisibleForTesting
    var oneShotState = 1

    suspend fun oneShot() : String {
        delay(40_000)
        return (oneShotState++).toString()
    }

    fun coldFlow() : Flow<Int> {
        return flow {
            (1..100).map {
                delay(1000)
                emit(it)
            }
        }
    }

    //We can use this to test an argument captor
    fun coldFlowWithStart(start : Int) : Flow<Int> {
        return flow { (start .. 100).map { delay(1000) ; emit(it) }}
    }

    fun flowWithState() : Flow<Int> {
        return coldFlow().map { it + oneShotState }
    }

    data class FlakeyFlowException(val value : Int) : Throwable("Flow was flakey for reasons on event $value")
    fun Flow<Int>.makeFlakeyExt(failWhen : (upstream : Int) -> Boolean) : Flow<Int> {
        return this.transformLatest {
            if (failWhen(it)) {
                throw FlakeyFlowException(it)
            } else {
                emit(it)
            }
        }
    }

    fun makeFlakey(upstream: Flow<Int>, failWhen: (upstream: Int) -> Boolean) = upstream.makeFlakeyExt(failWhen)
}