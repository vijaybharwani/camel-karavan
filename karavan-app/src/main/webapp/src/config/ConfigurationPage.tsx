import React from 'react';
import {
    Gallery,
    PageSection
} from '@patternfly/react-core';
import '../designer/karavan.css';

interface Props {
}

interface State {
    repository: string,
    path: string,
    integrations: []
}

export class ConfigurationPage extends React.Component<Props, State> {

    public state: State = {
        repository: '',
        path: '',
        integrations: []
    };

    componentDidMount() {

    }

    render() {
        return (
            <PageSection  padding={{ default: 'noPadding' }}>
                <Gallery hasGutter>
                    {/*{this.state.integrations.map(value => (*/}
                    {/*    // <IntegrationCard name={value}/>*/}
                    {/*))}*/}
                </Gallery>
            </PageSection>
        );
    }
};