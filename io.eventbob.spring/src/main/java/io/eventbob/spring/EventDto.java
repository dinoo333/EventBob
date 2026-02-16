package io.eventbob.spring;

import java.util.Map;

/**
 * Data Transfer Object for Event deserialization from JSON.
 *
 * <p>Plain POJO with JavaBean getters/setters for Jackson deserialization.
 * Keeps Jackson annotations out of the domain Event class.
 * Maps from/to domain Event via EventController mapping methods.
 */
public class EventDto {

  private String source;
  private String target;
  private Map<String, Object> parameters;
  private Map<String, Object> metadata;
  private Object payload;

  public EventDto() {
  }

  public EventDto(String source, String target, Map<String, Object> parameters,
      Map<String, Object> metadata, Object payload) {
    this.source = source;
    this.target = target;
    this.parameters = parameters;
    this.metadata = metadata;
    this.payload = payload;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(Object payload) {
    this.payload = payload;
  }

  @Override
  public String toString() {
    return "EventDto{" +
        "source='" + source + '\'' +
        ", target='" + target + '\'' +
        ", parameters=" + parameters +
        ", metadata=" + metadata +
        '}';
  }
}
