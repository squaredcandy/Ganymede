import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Truth
import com.squaredcandy.europa.util.Result
import com.squaredcandy.europa.util.isFailure
import com.squaredcandy.europa.util.isSuccess
import com.squaredcandy.europa.util.onSuccess

class ResultListSubject<T>(
    failureMetadata: FailureMetadata,
    private val actual: Result<List<T>>
) : Subject(failureMetadata, actual) {

    companion object {
        fun <T> assertThat(actual: Result<List<T>>): ResultListSubject<T> {
            return Truth.assertAbout(resultSubjectFactory<T>()).that(actual)
        }
        private fun <T> resultSubjectFactory() : Factory<ResultListSubject<T>, Result<List<T>>>
                = Factory<ResultListSubject<T>, Result<List<T>>>(::ResultListSubject)
    }

    fun isSuccess() {
        if(!actual.isSuccess()) {
            failWithActual(Fact.simpleFact("Expected success but got failure"))
        }
    }

    fun isFailure() {
        if(!actual.isFailure()) {
            failWithActual(Fact.simpleFact("Expected failure but got success"))
        }
    }

    fun isSuccessListEmpty() {
        isSuccess()
        actual.onSuccess {
            if(it.isNotEmpty()) {
                failWithActual(Fact.simpleFact("Expected list to be empty"))
            }
        }
    }

    fun isSuccessListContains(element: T) {
        isSuccess()
        actual.onSuccess {
            if(!it.contains(element)) {
                failWithActual(Fact.simpleFact("Expected list to contain $element"))
            }
        }
    }

    fun isSuccessListHasSize(size: Int) {
        isSuccess()
        actual.onSuccess {
            if(it.size != size) {
                failWithActual(Fact.simpleFact("Expected list to be of size $size but was ${it.size}"))
            }
        }
    }

    fun isFailureEqualTo(expected: Throwable) {
        isFailure()
        if((actual as Result.Failure).throwable != expected) {
            failWithActual(expected.toString(), actual)
        }
    }
}