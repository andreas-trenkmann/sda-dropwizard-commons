package org.sdase.commons.server.swagger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openapitools.jackson.dataformat.hal.HALLink;
import io.openapitools.jackson.dataformat.hal.annotation.Link;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Resource
@ApiModel(description = "Person")
public class PersonResource {
   @Link
   @ApiModelProperty("Link relation 'self': The HAL link referencing this file.")
   private HALLink self;

   @ApiModelProperty(value = "firstName", example = "John")
   private final String firstName;

   @ApiModelProperty(value = "lastName", example = "Doe")
   private final String lastName;

   @ApiModelProperty(value = "traits", example = "[\"hipster\", \"generous\"]")
   private final List<String> traits = new ArrayList<>();

   @JsonCreator
   public PersonResource(
         @JsonProperty("firstName") String firstName,
         @JsonProperty("lastName") String lastName,
         @JsonProperty("traits") List<String> traits, HALLink self) {

      this.firstName = firstName;
      this.lastName = lastName;
      this.traits.addAll(traits);
   }

   public HALLink getSelf() {
      return self;
   }

   public String getFirstName() {
      return firstName;
   }

   public String getLastName() {
      return lastName;
   }

   public List<String> getTraits() {
      return traits;
   }
}
