package io.github.rehody.abplatform.config;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public abstract class AbstractWebMvcTest {

    private static final LocalValidatorFactoryBean VALIDATOR = createValidator();

    protected MockMvc buildStandaloneMockMvc(Object controller, Object... controllerAdvice) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(controllerAdvice)
                .setValidator(VALIDATOR)
                .build();
    }

    private static LocalValidatorFactoryBean createValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
