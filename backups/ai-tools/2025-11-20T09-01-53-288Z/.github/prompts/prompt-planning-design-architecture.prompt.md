# design-architecture

Design system architecture for a new feature or project

## Variables

- `{{requirements}}` (required): Feature or project requirements
- `{{constraints}}`: Technical or business constraints
- `{{existing_system}}`: Description of existing system to integrate with

## Prompt

Design architecture for:

Requirements:
{{requirements}}

{{#constraints}}
Constraints:
{{constraints}}
{{/constraints}}

{{#existing_system}}
Existing system:
{{existing_system}}
{{/existing_system}}

Provide:

1. **Problem Analysis**: Understanding of requirements and constraints

2. **Proposed Architecture**: 
   - High-level component diagram (Mermaid)
   - Component responsibilities
   - Communication patterns

3. **Alternative Approaches**: Other options considered
   - Pros and cons of each
   - Why the proposed approach was chosen

4. **Key Design Decisions**:
   - Data storage strategy
   - API design
   - Security model
   - Error handling approach
   - Logging and monitoring

5. **Technology Recommendations**:
   - Frameworks and libraries
   - Infrastructure requirements
   - Justification for choices

6. **Integration Points**: How it connects to existing systems

7. **Non-Functional Requirements**:
   - Performance targets
   - Scalability considerations
   - Security requirements
   - Reliability and availability

8. **Risks and Mitigations**: Potential issues and how to address them

