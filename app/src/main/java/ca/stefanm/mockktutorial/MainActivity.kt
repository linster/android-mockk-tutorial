package ca.stefanm.mockktutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import ca.stefanm.mockktutorial.ui.theme.MockKTutorialTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MockKTutorialTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting("Android")
                }
            }
        }
    }
}




@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
            text = "Hello $name!",
            modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MockKTutorialTheme {
        Greeting("Android")
    }
}

//TODO composable that takes in data as paremeters

//TODO compsable that emits events of button clicks

//TODO composable that holds everything and takes in a repository as a parameter




class MainActivityViewModel : ViewModel() {

}



class PrimaryRepository @Inject constructor(
    private val secondaryRepository : SecondaryRepository
) {

    sealed interface FlakeyFlowResult {
        data class Ok(val value : Int) : FlakeyFlowResult
        data class Failed(val cause : Throwable) : FlakeyFlowResult
    }


    fun prepareFlakeyData() : Flow<FlakeyFlowResult> {
        return secondaryRepository.flakeyFlow()
            .map { FlakeyFlowResult.Ok(it) as FlakeyFlowResult }
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
    fun Flow<Int>.makeFlakey(failWhen : (upstream : Int) -> Boolean) : Flow<Int> {
        return this.transformLatest {
            if (failWhen(it)) {
                throw FlakeyFlowException(it)
            } else {
                emit(it)
            }
        }
    }

    fun makeFlakey(upstream: Flow<Int>, failWhen: (upstream: Int) -> Boolean) = upstream.makeFlakey(failWhen)
}