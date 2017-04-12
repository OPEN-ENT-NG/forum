import {RTE } from 'entcore/entcore';
import { $ } from 'entcore/libs/jquery/jquery';

export let forumExtensions = {
    extendEditor: function () {
        RTE.baseToolbarConf.option('quote', function (instance) {
            return {
                template: '<i ng-click="quoteOption.display.enterQuote = true" tooltip="editor.option.quote"></i>' +
                '<lightbox show="quoteOption.display.enterQuote" on-close="quoteOption.display.enterQuote = false;">' +
                '<h2>Citation</h2>' +
                '<label style="line-height: 30px" class="three cell">Message d\'origine</label>' +
                '<select-list display="quoteOption.message" display-as="authorName" placeholder="Message" class="nine cell">' +
                '<opt ng-repeat="message in messages.all" ng-click="fillMessage(message)">[[getMessagePreview(message)]]</opt>' +
                '</select-list>' +
                '<div class="row"><div contenteditable></div></div>' +
                '<div class="row"><button class="right-magnet" ng-click="addQuote()">Ajouter</button>' +
                '<button class="cancel right-magnet" ng-click="quoteOption.display.enterQuote = false;">' +
                '<i18n>cancel</i18n></button></div>' +
                '</lightbox>',
                link: function (scope, element, attributes) {
                    scope.quoteOption = {
                        message: undefined,
                        display: { enterQuote: false }
                    };

                    scope.getMessagePreview = function (message) {
                        return $(message.content).text().slice(0, 75) + '...';
                    };

                    scope.fillMessage = function (message) {
                        scope.quoteOption.message = message;
                        element.find('[contenteditable]').html(instance.compile(scope.quoteOption.message.content)(scope));
                    };

                    scope.addQuote = function () {
                        var quote = $('<blockquote class="quotation"></blockquote>');
                        quote.html(element.find('[contenteditable]').html());
                        var footer = $('<footer>- ' + scope.quoteOption.message.authorName + '</footer>');
                        footer.appendTo(quote);
                        var htmlContent = instance.compile(quote[0].outerHTML + '<div><br/></div>')(scope);
                        var text = '';
                        for(var i = 0; i < htmlContent.length; i++){
                            text += htmlContent[i].outerHTML;
                        }
                        instance.selection.replaceHTML(text);
                        scope.quoteOption.display.enterQuote = false;
                    };
                }
            }
        });
    }
}

