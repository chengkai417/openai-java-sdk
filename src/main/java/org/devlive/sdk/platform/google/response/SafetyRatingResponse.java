package org.devlive.sdk.platform.google.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SafetyRatingResponse
{
    @JsonProperty(value = "category")
    private String category;

    @JsonProperty(value = "probability")
    private String probability;
}
