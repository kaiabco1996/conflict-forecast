package aero.airlab.challenge.conflictforecast

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
@RestController
@RequestMapping("/v2")
class ConflictForecastApplication{

	@GetMapping
	fun helloWorld() = "Hello world"
}

fun main(args: Array<String>) {
	runApplication<ConflictForecastApplication>(*args)
}
