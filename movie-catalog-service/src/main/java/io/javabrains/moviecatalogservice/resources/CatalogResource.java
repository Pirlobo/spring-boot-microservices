package io.javabrains.moviecatalogservice.resources;

import io.javabrains.moviecatalogservice.models.CatalogItem;
import io.javabrains.moviecatalogservice.models.Movie;
import io.javabrains.moviecatalogservice.models.Rating;
import io.javabrains.moviecatalogservice.models.UserRating;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/catalog")
public class CatalogResource {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    WebClient.Builder webClientBuilder;
       
    @RequestMapping("/{userId}")
    @HystrixCommand(fallbackMethod = "getFallBackCatalog")
    public List<CatalogItem> getCatalog(@PathVariable("userId") String userId) {

        UserRating userRating = getUserRating(userId);

        return userRating.getRatings().stream()
                .map(rating -> getCatalogItem(rating))
               .collect(Collectors.toList());
    }
    
    // Remember to refractor the code , then inject them as beans to be more conventional and readable
    
    // Handle first API call 
    @HystrixCommand(fallbackMethod = "getFallBackUserRating",
    		commandProperties = {
    				// wait for 2 seconds, if no response, then it causes time out (1 thread is failed)
    				@HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "2000") ,
    				// looking for the last 6 thread
    				@HystrixProperty(name = "circuitBreaker.requestVolume.Threshold", value = "6") ,
    				// If 50% of 6 threads which is 3 threads are failed, then trigger the circuit breaker
    				@HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50") ,
    				// The circuit breaker will sleep for 5 seconds (stop calling api for 5 seconds) to get back to normal
    				@HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", value = "5000")
    		}
    		)
    public UserRating getUserRating(@PathVariable("userId") String userId) {
    	return restTemplate.getForObject("http://ratings-data-service/ratingsdata/user/" + userId, UserRating.class);
    }
    public UserRating getFallBackUserRating(@PathVariable("userId") String userId) {
    	UserRating userRating = new UserRating();
    	userRating.setUserId(userId);
    	userRating.setRatings(Arrays.asList(new Rating("0", 0)));
    	return userRating;
    }
    
    // Handle 2nd api call
    @HystrixCommand(fallbackMethod = "getFallBackCatalogItem")
    public CatalogItem getCatalogItem(Rating rating) {
    	Movie movie = restTemplate.getForObject("http://movie-info-service/movies/" + rating.getMovieId(), Movie.class);
        return new CatalogItem(movie.getName(), movie.getDescription(), rating.getRating());
    }
    
    public CatalogItem getFallBackCatalogItem (Rating rating) {
    	return new CatalogItem("Movie not found", "", rating.getRating());
    }
    
    
    
    
    //we should specify the fallback methods corresponding to every external api.
    
//    // if we close one service (dependency services), then the system will not be crashed because we have just handled the fallback method 
//    public List<CatalogItem> getFallBackCatalog(@PathVariable("userId") String userId) {
//    	return Arrays.asList(new CatalogItem("No Movie", "", 0));
//    }
}

/*
Alternative WebClient way
Movie movie = webClientBuilder.build().get().uri("http://localhost:8082/movies/"+ rating.getMovieId())
.retrieve().bodyToMono(Movie.class).block();
*/