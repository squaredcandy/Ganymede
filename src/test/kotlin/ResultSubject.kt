import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.Subject
import com.google.common.truth.Subject.Factory
import com.google.common.truth.Truth.assertAbout
import com.squaredcandy.europa.util.Result
import com.squaredcandy.europa.util.isFailure
import com.squaredcandy.europa.util.isSuccess

class ResultSubject<T>(
    failureMetadata: FailureMetadata,
    private val actual: Result<T>
) : Subject(failureMetadata, actual) {

    companion object {
        fun <T> assertThat(actual: Result<T>): ResultSubject<T> {
            return assertAbout(resultSubjectFactory<T>()).that(actual)
        }
        private fun <T> resultSubjectFactory() : Factory<ResultSubject<T>, Result<T>>
                = Factory<ResultSubject<T>, Result<T>>(::ResultSubject)
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

    fun isSuccessEqualTo(expected: T) {
        isSuccess()
        if((actual as Result.Success<T>).value != expected) {
            failWithActual(expected.toString(), actual)
        }
    }

    fun isFailureEqualTo(expected: Throwable) {
        isFailure()
        if((actual as Result.Failure).throwable != expected) {
            failWithActual(expected.toString(), actual)
        }
    }
}
